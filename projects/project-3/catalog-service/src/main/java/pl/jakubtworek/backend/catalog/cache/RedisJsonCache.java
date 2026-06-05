package pl.jakubtworek.backend.catalog.cache;

import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Supplier;

/**
 * Prosty cache JSON oparty o Redis.
 *
 * Implementuje wzorzec cache-aside:
 *
 * 1. Najpierw próbujemy odczytać wartość z cache.
 * 2. Jeśli jest cache hit, zwracamy wartość z Redisa.
 * 3. Jeśli jest cache miss, pobieramy wartość ze źródła prawdy przez loader.
 * 4. Następnie zapisujemy wynik do cache z TTL.
 * 5. Jeśli Redis nie działa, system nadal działa, tylko bez cache.
 *
 * W tym projekcie źródłem prawdy jest PostgreSQL, a Redis jest tylko warstwą przyspieszającą.
 * Awaria Redisa nie powinna powodować niedostępności katalogu wydarzeń.
 */
@Component
public class RedisJsonCache {

    private static final Logger log = LoggerFactory.getLogger(RedisJsonCache.class);

    /**
     * Springowy klient Redis dla prostych operacji na Stringach.
     *
     * Trzymamy wartości jako JSON, dlatego wystarcza StringRedisTemplate:
     *
     * - key   -> String,
     * - value -> JSON String.
     */
    private final StringRedisTemplate redisTemplate;

    /**
     * ObjectMapper służy do serializacji obiektów do JSON i deserializacji JSON z Redisa.
     *
     * Dzięki przekazywaniu JavaType do metody getOrLoad możemy poprawnie odczytywać także
     * typy generyczne, np. List<EventResponse>.
     */
    private final ObjectMapper objectMapper;

    /**
     * MeterRegistry pozwala rejestrować metryki cache dla Prometheusa/Grafany.
     *
     * Dzięki temu możemy mierzyć:
     *
     * - cache hit,
     * - cache miss,
     * - Redis unavailable,
     * - read_error,
     * - write_error,
     * - write.
     */
    private final MeterRegistry meterRegistry;

    /**
     * Lokalna mapa locków używana do single-flight per instancja aplikacji.
     *
     * Problem:
     * Gdy popularny klucz cache wygasa, wiele requestów może jednocześnie zauważyć cache miss
     * i równolegle uderzyć do bazy danych. To nazywa się cache stampede.
     *
     * Rozwiązanie tutaj:
     * Dla tego samego redisKey tylko jeden wątek w danej instancji wykonuje loader.
     * Pozostałe wątki czekają, a po wejściu do sekcji synchronized ponownie sprawdzają cache.
     *
     * Ograniczenie:
     * To zabezpiecza tylko pojedynczą instancję aplikacji. Przy wielu replikach nadal może
     * wystąpić równoległy load z kilku instancji. Produkcyjnie można rozważyć Redis lock
     * albo bardziej zaawansowany mechanizm request coalescing.
     */
    private final ConcurrentHashMap<String, Object> locks = new ConcurrentHashMap<>();

    public RedisJsonCache(StringRedisTemplate redisTemplate, ObjectMapper objectMapper, MeterRegistry meterRegistry) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
        this.meterRegistry = meterRegistry;
    }

    /**
     * Pobiera wartość z cache albo ładuje ją ze źródła danych.
     *
     * @param cacheName logiczna nazwa cache, np. "events", "event-details", "event-availability"
     * @param key       klucz biznesowy w ramach cache, np. "all" albo eventId
     * @param ttl       czas życia wpisu w Redisie
     * @param javaType  typ Java potrzebny do deserializacji JSON-a
     * @param loader    funkcja pobierająca wartość ze źródła prawdy, np. z PostgreSQL
     * @return wartość z cache albo wartość załadowana przez loader
     */
    public <T> T getOrLoad(String cacheName, String key, Duration ttl, JavaType javaType, Supplier<T> loader) {
        /*
         * Budujemy jednoznaczny klucz Redisa.
         *
         * Przykłady:
         *
         * cache:events:all
         * cache:event-details:11111111-1111-1111-1111-111111111111
         * cache:event-availability:11111111-1111-1111-1111-111111111111
         */
        String redisKey = "cache:" + cacheName + ":" + key;

        /*
         * Pierwsza próba odczytu bez locka.
         *
         * To jest szybka ścieżka dla cache hit. Nie chcemy synchronizować każdego requestu,
         * bo cache hit powinien być tani.
         */
        T cached = read(redisKey, cacheName, javaType);

        if (cached != null) {
            return cached;
        }

        /*
         * Cache miss.
         *
         * Tworzymy albo pobieramy lokalny lock dla konkretnego redisKey.
         * Dzięki temu różne klucze cache nie blokują się wzajemnie.
         */
        Object lock = locks.computeIfAbsent(redisKey, ignored -> new Object());

        synchronized (lock) {
            try {
                /*
                 * Druga próba odczytu po wejściu do locka.
                 *
                 * To jest ważny element single-flight:
                 * inny wątek mógł już załadować wartość i zapisać ją do Redisa,
                 * gdy aktualny wątek czekał na lock.
                 */
                cached = read(redisKey, cacheName, javaType);

                if (cached != null) {
                    return cached;
                }

                /*
                 * Nadal cache miss, więc dopiero teraz wykonujemy loader.
                 *
                 * Loader powinien pobierać dane ze źródła prawdy, np. z bazy danych.
                 */
                T value = loader.get();

                /*
                 * Po udanym loadzie zapisujemy wartość do cache.
                 *
                 * Jeśli zapis do Redisa się nie uda, write() tylko zaloguje problem,
                 * a użytkownik i tak dostanie poprawną wartość z loadera.
                 */
                write(redisKey, cacheName, ttl, value);

                return value;
            } finally {
                /*
                 * Usuwamy lock z mapy, żeby nie gromadzić w pamięci locków dla starych kluczy.
                 *
                 * Uwaga:
                 * Usunięcie locka w finally jest poprawne dla prostego single-flight,
                 * ale przy bardzo wysokiej równoległości trzeba uważać na subtelne wyścigi
                 * między computeIfAbsent, synchronized i remove. Do projektu edukacyjnego
                 * to wystarczy, produkcyjnie warto byłoby to przetestować mocniej.
                 */
                locks.remove(redisKey);
            }
        }
    }

    /**
     * Próbuje odczytać wartość z Redisa i zdeserializować JSON do oczekiwanego typu.
     *
     * Zwraca null, jeśli:
     *
     * - nie ma wpisu w cache,
     * - Redis jest niedostępny,
     * - wartość w Redisie jest uszkodzona albo niezgodna z oczekiwanym typem.
     *
     * Null oznacza: "idź do loadera".
     */
    private <T> T read(String redisKey, String cacheName, JavaType javaType) {
        try {
            String json = redisTemplate.opsForValue().get(redisKey);

            /*
             * Cache miss.
             *
             * Nie ma wpisu dla tego klucza, więc caller powinien pobrać dane ze źródła prawdy.
             */
            if (json == null) {
                counter("miss", cacheName).increment();
                return null;
            }

            /*
             * Cache hit.
             *
             * Zliczamy hit przed deserializacją, bo Redis faktycznie zwrócił wartość.
             * Jeśli JSON okaże się uszkodzony, dodatkowo zliczymy read_error w catch niżej.
             */
            counter("hit", cacheName).increment();

            return objectMapper.readValue(json, javaType);
        } catch (DataAccessException exception) {
            /*
             * Redis jest niedostępny albo wystąpił błąd komunikacji.
             *
             * To nie powinno zatrzymać aplikacji. Catalog Service ma działać dalej,
             * tylko z większą liczbą odczytów z bazy.
             */
            counter("unavailable", cacheName).increment();

            log.warn("Redis cache unavailable. Falling back to source. key={}", redisKey, exception);

            return null;
        } catch (Exception exception) {
            /*
             * Wartość istnieje w Redisie, ale nie da się jej odczytać jako oczekiwanego typu.
             *
             * Możliwe przyczyny:
             *
             * - zmiana struktury DTO,
             * - ręcznie wpisana zła wartość,
             * - częściowo uszkodzony JSON,
             * - błąd serializacji w starszej wersji aplikacji.
             *
             * Najbezpieczniej usunąć taki wpis i pobrać dane ze źródła prawdy.
             */
            counter("read_error", cacheName).increment();

            log.warn("Redis cache read failed. Evicting bad value. key={}", redisKey, exception);

            try {
                redisTemplate.delete(redisKey);
            } catch (Exception ignored) {
                /*
                 * Best effort.
                 *
                 * Jeśli nie uda się usunąć błędnej wartości, nie blokujemy requestu.
                 * Kolejne requesty mogą znowu spróbować i ponownie przejść do loadera.
                 */
            }

            return null;
        }
    }

    /**
     * Zapisuje wartość do Redisa jako JSON z TTL.
     *
     * Błąd zapisu do cache nie jest błędem biznesowym. Użytkownik nadal dostaje wartość,
     * którą loader pobrał ze źródła prawdy.
     */
    private void write(String redisKey, String cacheName, Duration ttl, Object value) {
        Objects.requireNonNull(value, "value");

        try {
            /*
             * Dodajemy jitter do TTL.
             *
             * Problem:
             * Jeśli wiele kluczy dostanie identyczny TTL, mogą wygasnąć w tym samym momencie.
             * To może spowodować nagły skok ruchu do bazy, czyli cache stampede.
             *
             * Jitter rozprasza momenty wygaśnięcia wpisów cache.
             */
            Duration ttlWithJitter = ttl.plusSeconds(ThreadLocalRandom.current().nextLong(1, 11));

            redisTemplate.opsForValue().set(
                    redisKey,
                    objectMapper.writeValueAsString(value),
                    ttlWithJitter
            );

            counter("write", cacheName).increment();
        } catch (DataAccessException exception) {
            /*
             * Redis niedostępny podczas zapisu.
             *
             * Nie przerywamy requestu, bo dane są już pobrane z DB.
             * Skutek uboczny: kolejne requesty mogą ponownie trafić do bazy.
             */
            counter("unavailable", cacheName).increment();

            log.warn("Redis cache unavailable. Returning uncached value. key={}", redisKey, exception);
        } catch (Exception exception) {
            /*
             * Błąd serializacji albo inny błąd zapisu.
             *
             * Również nie przerywamy requestu. Cache ma poprawiać wydajność,
             * ale nie może być wymagany do poprawności działania endpointu.
             */
            counter("write_error", cacheName).increment();

            log.warn("Redis cache write failed. key={}", redisKey, exception);
        }
    }

    /**
     * Tworzy licznik Micrometer dla operacji cache.
     *
     * Nazwa metryki:
     *
     * app_cache_requests_total
     *
     * Tagi:
     *
     * - cache  -> nazwa cache, np. events albo event-availability,
     * - result -> hit, miss, write, unavailable, read_error, write_error.
     *
     * Dzięki temu w Prometheus/Grafana można analizować skuteczność cache i awarie Redisa.
     */
    private Counter counter(String result, String cacheName) {
        return Counter.builder("app_cache_requests_total")
                .tag("cache", cacheName)
                .tag("result", result)
                .register(meterRegistry);
    }
}