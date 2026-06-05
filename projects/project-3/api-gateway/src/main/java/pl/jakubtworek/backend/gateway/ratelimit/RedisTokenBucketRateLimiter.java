package pl.jakubtworek.backend.gateway.ratelimit;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.util.List;

/**
 * Redisowa implementacja rate limitera opartego o algorytm token bucket.
 *
 * Token bucket działa tak:
 *
 * - bucket ma maksymalną pojemność, np. 600 tokenów,
 * - każdy request zużywa określoną liczbę tokenów, tutaj zawsze 1,
 * - tokeny odnawiają się co określony czas, np. 600 tokenów co 60 sekund,
 * - jeśli tokeny są dostępne, request przechodzi,
 * - jeśli tokenów brakuje, request dostaje 429 Too Many Requests.
 *
 * Redis jest użyty celowo, bo API Gateway może mieć wiele instancji.
 * Gdyby każda instancja trzymała licznik lokalnie w pamięci, limit byłby liczony osobno
 * dla każdej repliki i nie chroniłby systemu globalnie.
 */
@Component
public class RedisTokenBucketRateLimiter {

    /**
     * Lua script wykonywany po stronie Redisa.
     *
     * To jest ważne, bo operacja rate limitingu musi być atomowa:
     *
     * 1. odczytaj aktualną liczbę tokenów,
     * 2. dolicz tokeny po upływie czasu,
     * 3. sprawdź, czy request może przejść,
     * 4. odejmij token,
     * 5. zapisz nowy stan.
     *
     * Gdyby te kroki były wykonywane osobnymi komendami Redis z aplikacji,
     * przy wielu równoległych requestach mogłyby pojawić się race condition.
     *
     * Lua script w Redisie wykonuje się atomowo, więc kilka instancji gatewaya
     * nie popsuje wspólnego licznika.
     */
    private static final String SCRIPT = """
            -- Klucz bucketa, np.:
            -- rate-limit:api-key:load-test:GET:/events
            local key = KEYS[1]

            -- Maksymalna liczba tokenów w buckecie.
            -- Przykład: 600.
            local capacity = tonumber(ARGV[1])

            -- Ile tokenów dodajemy przy jednym odnowieniu.
            -- Przykład: 600.
            local refillTokens = tonumber(ARGV[2])

            -- Co ile milisekund następuje odnowienie tokenów.
            -- Przykład: 60000 ms.
            local refillPeriodMs = tonumber(ARGV[3])

            -- Ile tokenów chce zużyć aktualny request.
            -- W tym projekcie zawsze 1 request = 1 token.
            local requested = tonumber(ARGV[4])

            -- Aktualny czas w milisekundach, liczony po stronie aplikacji.
            local nowMs = tonumber(ARGV[5])

            -- TTL klucza w Redisie. Dzięki temu nie trzymamy wiecznie bucketów
            -- dla klientów, którzy już przestali wysyłać requesty.
            local ttlSeconds = tonumber(ARGV[6])

            -- Odczytujemy aktualny stan bucketa z hasha Redis:
            -- tokens    - obecna liczba tokenów,
            -- updatedAt - timestamp ostatniego uzupełnienia bucketa.
            local bucket = redis.call('HMGET', key, 'tokens', 'updatedAt')
            local tokens = tonumber(bucket[1])
            local updatedAt = tonumber(bucket[2])

            -- Jeśli bucket jeszcze nie istnieje, tworzymy go jako pełny.
            -- To oznacza, że pierwszy request klienta nie jest karany oczekiwaniem.
            if tokens == nil then
              tokens = capacity
              updatedAt = nowMs
            end

            -- Liczymy, ile czasu minęło od ostatniego uzupełnienia.
            -- math.max chroni przed sytuacją, gdy zegar aplikacji cofnie się
            -- albo requesty przyjdą z minimalnie różnymi timestampami.
            local elapsed = math.max(0, nowMs - updatedAt)

            -- Liczymy, ile tokenów należy dodać.
            --
            -- Używamy math.floor, czyli uzupełniamy tokeny skokowo:
            -- jeśli refillPeriodMs = 60000, to tokeny wrócą po pełnej minucie,
            -- a nie płynnie co milisekundę.
            local refill = math.floor(elapsed / refillPeriodMs) * refillTokens

            -- Nie pozwalamy przekroczyć maksymalnej pojemności bucketa.
            tokens = math.min(capacity, tokens + refill)

            -- Jeśli faktycznie dodaliśmy tokeny, aktualizujemy timestamp.
            if refill > 0 then
              updatedAt = nowMs
            end

            -- Domyślnie request nie jest dozwolony.
            local allowed = 0

            -- Jeśli mamy wystarczająco dużo tokenów, request jest dozwolony
            -- i odejmujemy requested tokenów.
            if tokens >= requested then
              allowed = 1
              tokens = tokens - requested
            end

            -- Jeśli request nie jest dozwolony, wyliczamy Retry-After.
            -- To mówi klientowi, po ilu sekundach może spróbować ponownie.
            local retryAfterMs = 0
            if allowed == 0 then
              local missing = requested - tokens
              local periods = math.ceil(missing / refillTokens)
              retryAfterMs = periods * refillPeriodMs
            end

            -- Zapisujemy nowy stan bucketa.
            redis.call('HMSET', key, 'tokens', tokens, 'updatedAt', updatedAt)

            -- Ustawiamy TTL, żeby Redis automatycznie usuwał nieaktywne buckety.
            redis.call('EXPIRE', key, ttlSeconds)

            -- Zwracamy:
            -- 1. czy request był dozwolony,
            -- 2. ile tokenów zostało,
            -- 3. Retry-After w sekundach.
            return { allowed, tokens, math.ceil(retryAfterMs / 1000) }
            """;

    private final StringRedisTemplate redisTemplate;

    /**
     * Clock jest wydzielony jako pole zamiast bezpośredniego System.currentTimeMillis().
     *
     * Dzięki temu kod byłby łatwiejszy do testowania, gdybyśmy dodali konstruktor
     * przyjmujący Clock.fixed(...) w testach jednostkowych.
     */
    private final Clock clock;

    /**
     * Reprezentacja Lua script dla Spring Data Redis.
     *
     * Wynikiem skryptu jest lista:
     *
     * [allowed, remainingTokens, retryAfterSeconds]
     */
    private final DefaultRedisScript<List> script;

    public RedisTokenBucketRateLimiter(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
        this.clock = Clock.systemUTC();
        this.script = new DefaultRedisScript<>(SCRIPT, List.class);
    }

    /**
     * Próbuje zużyć jeden token z bucketa.
     *
     * @param key    klucz Redis identyfikujący bucket klienta i endpointu
     * @param bucket konfiguracja limitu, np. capacity/refillTokens/refillPeriod
     * @return decyzja rate limitera: allowed / remaining tokens / retry-after
     */
    public TokenBucketDecision consume(String key, RateLimitProperties.Bucket bucket) {
        /*
         * Okres odnawiania tokenów w milisekundach.
         *
         * Math.max(1, ...) chroni przed błędną konfiguracją typu Duration.ZERO,
         * która spowodowałaby dzielenie przez zero w Lua script.
         */
        long refillMs = Math.max(1, bucket.refillPeriod().toMillis());

        /*
         * TTL klucza bucketa.
         *
         * Ustawiamy minimum 60 sekund oraz około 3 okresy refill.
         * Dzięki temu:
         *
         * - aktywni klienci mają stale odnawiany bucket,
         * - nieaktywni klienci nie zostawiają śmieci w Redisie na zawsze.
         */
        long ttlSeconds = Math.max(60, bucket.refillPeriod().toSeconds() * 3);

        /*
         * Wykonujemy Lua script w Redisie.
         *
         * KEYS:
         * - key
         *
         * ARGV:
         * - capacity,
         * - refillTokens,
         * - refillPeriodMs,
         * - requested,
         * - nowMs,
         * - ttlSeconds.
         */
        List<?> result = redisTemplate.execute(
                script,
                List.of(key),
                String.valueOf(bucket.capacity()),
                String.valueOf(bucket.refillTokens()),
                String.valueOf(refillMs),
                "1",
                String.valueOf(clock.millis()),
                String.valueOf(ttlSeconds)
        );

        /*
         * Jeśli Redis zwróci null albo niepełny wynik, przyjmujemy fail-open.
         *
         * To jest spójne z RateLimitFilter, który przy awarii Redisa też przepuszcza ruch.
         * Trade-off: system pozostaje dostępny, ale chwilowo traci ochronę przed nadmiernym ruchem.
         */
        if (result == null || result.size() < 3) {
            return new TokenBucketDecision(true, -1, 0);
        }

        long allowed = asLong(result.get(0));
        long remaining = asLong(result.get(1));
        long retryAfter = asLong(result.get(2));

        return new TokenBucketDecision(allowed == 1, remaining, retryAfter);
    }

    /**
     * Konwertuje wartości zwrócone przez Redis/Lua na long.
     *
     * W zależności od sterownika i sposobu serializacji Redis może zwrócić liczby
     * jako Number albo jako tekst. Ten helper obsługuje oba przypadki.
     */
    private long asLong(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }

        return Long.parseLong(String.valueOf(value));
    }
}