package pl.jakubtworek.booking.cache;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.Profile;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Optional;
import java.util.UUID;

/**
 * Redisowa implementacja cache detali eventu.
 *
 * Klasa jest aktywna tylko dla profilu:
 *
 * nosql-real
 *
 * Dzięki temu domyślne uruchomienie aplikacji i testy nie muszą wymagać Redisa.
 * W profilu testowym/domowym można używać implementacji in-memory.
 *
 * Ten cache jest częścią wzorca cache-aside:
 *
 * - serwis najpierw próbuje odczytać dane z cache,
 * - jeśli ich nie ma, pobiera dane z PostgreSQL,
 * - następnie zapisuje wynik do cache z TTL,
 * - po zmianie eventu albo dostępności cache powinien zostać usunięty.
 *
 * Ważne:
 * Redis nie jest źródłem prawdy. Źródłem prawdy nadal pozostaje PostgreSQL.
 */
@Component
@Profile("nosql-real")
public class RedisEventDetailsCache implements EventDetailsCache {

    /**
     * Prefix kluczy w Redisie.
     *
     * Przykładowy klucz:
     *
     * event-details:550e8400-e29b-41d4-a716-446655440000
     *
     * Prefix pomaga rozróżniać różne typy danych zapisane w Redisie.
     */
    private static final String PREFIX = "event-details:";

    /**
     * StringRedisTemplate pracuje na tekstowych kluczach i wartościach.
     *
     * Ponieważ EventCacheEntry jest obiektem Javy, zapisujemy go jako JSON.
     */
    private final StringRedisTemplate redis;

    /**
     * ObjectMapper odpowiada za serializację i deserializację wpisu cache.
     *
     * To proste rozwiązanie edukacyjne. Produkcyjnie warto pilnować wersjonowania
     * formatu JSON, jeśli cache może przetrwać deploy aplikacji.
     */
    private final ObjectMapper objectMapper;

    /**
     * Constructor injection.
     *
     * Redis i ObjectMapper są dostarczane przez Springa.
     */
    public RedisEventDetailsCache(StringRedisTemplate redis, ObjectMapper objectMapper) {
        this.redis = redis;
        this.objectMapper = objectMapper;
    }

    /**
     * Próbuje pobrać detale eventu z Redis.
     *
     * Jeśli klucz nie istnieje, zwracamy Optional.empty().
     * Może to oznaczać:
     *
     * - cache miss,
     * - wygaśnięcie TTL,
     * - wcześniejszą invalidację cache,
     * - brak wcześniejszego zapisu do cache.
     */
    @Override
    public Optional<EventCacheEntry> get(UUID eventId) {
        String json = redis.opsForValue().get(PREFIX + eventId);

        if (json == null) {
            return Optional.empty();
        }

        try {
            /*
             * Deserializujemy JSON z Redisa do obiektu Javy.
             */
            return Optional.of(objectMapper.readValue(json, EventCacheEntry.class));
        } catch (JsonProcessingException ex) {
            /*
             * Jeśli wpis w cache jest uszkodzony albo ma niekompatybilny format,
             * usuwamy go i traktujemy jako cache miss.
             *
             * Dla cache to rozsądne zachowanie: lepiej pobrać świeże dane z SQL
             * niż zwracać błędny wpis.
             */
            evict(eventId);
            return Optional.empty();
        }
    }

    /**
     * Zapisuje detale eventu do Redis z określonym TTL.
     *
     * TTL jest ważny, bo ogranicza czas życia potencjalnie nieaktualnych danych.
     *
     * Przykład:
     *
     * redis.set("event-details:{id}", json, ttl)
     *
     * Po upływie TTL Redis automatycznie usunie wpis.
     */
    @Override
    public void put(EventCacheEntry entry, Duration ttl) {
        try {
            redis.opsForValue().set(
                    PREFIX + entry.eventId(),
                    objectMapper.writeValueAsString(entry),
                    ttl
            );
        } catch (JsonProcessingException ex) {
            /*
             * Brak możliwości serializacji oznacza błąd techniczny aplikacji.
             *
             * Nie ukrywamy go jako cache miss, bo put(...) jest operacją zapisu
             * wykonywaną po poprawnym pobraniu danych z SQL.
             */
            throw new IllegalStateException("Cannot serialize event cache entry", ex);
        }
    }

    /**
     * Usuwa wpis cache dla eventu.
     *
     * Powinno być wykonywane po zmianach, które wpływają na dane eventu
     * albo jego dostępność, np.:
     *
     * - utworzenie rezerwacji,
     * - anulowanie rezerwacji,
     * - zmiana danych eventu.
     *
     * To jest klasyczna invalidacja cache.
     */
    @Override
    public void evict(UUID eventId) {
        redis.delete(PREFIX + eventId);
    }
}