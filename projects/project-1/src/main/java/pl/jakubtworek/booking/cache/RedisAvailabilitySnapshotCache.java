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
 * Redisowa implementacja cache snapshotu dostępności eventu.
 *
 * Klasa jest aktywna tylko dla profilu:
 *
 * nosql-real
 *
 * Dzięki temu Redis jest wymagany dopiero wtedy, gdy świadomie uruchomisz aplikację
 * z profilem nosql-real. W testach albo domyślnym profilu można używać
 * implementacji in-memory.
 *
 * Ten cache przechowuje krótkotrwały snapshot dostępności:
 *
 * - totalCapacity,
 * - availableCapacity,
 * - moment utworzenia snapshotu,
 * - moment wygaśnięcia snapshotu.
 *
 * Ważne:
 * snapshot dostępności nie jest źródłem prawdy. Źródłem prawdy pozostaje
 * PostgreSQL i tabela capacity_pools.
 */
@Component
@Profile("nosql-real")
public class RedisAvailabilitySnapshotCache implements AvailabilitySnapshotCache {

    /**
     * Prefix kluczy w Redisie.
     *
     * Przykładowy klucz:
     *
     * availability:550e8400-e29b-41d4-a716-446655440000
     *
     * Prefix pomaga rozróżniać różne typy danych zapisane w Redisie.
     */
    private static final String PREFIX = "availability:";

    /**
     * StringRedisTemplate zapisuje dane jako tekst.
     *
     * Ponieważ AvailabilitySnapshot jest obiektem Javy, zapisujemy go jako JSON.
     */
    private final StringRedisTemplate redis;

    /**
     * ObjectMapper służy do serializacji i deserializacji snapshotu.
     *
     * To proste rozwiązanie edukacyjne. Produkcyjnie warto pilnować kompatybilności
     * formatu JSON między wersjami aplikacji.
     */
    private final ObjectMapper objectMapper;

    /**
     * Constructor injection.
     */
    public RedisAvailabilitySnapshotCache(StringRedisTemplate redis, ObjectMapper objectMapper) {
        this.redis = redis;
        this.objectMapper = objectMapper;
    }

    /**
     * Próbuje pobrać snapshot dostępności z Redis.
     *
     * Jeśli klucz nie istnieje, zwracamy Optional.empty().
     *
     * Może to oznaczać:
     * - cache miss,
     * - wygaśnięcie TTL,
     * - wcześniejszą invalidację po rezerwacji/anulowaniu,
     * - brak wcześniejszego zapisu.
     */
    @Override
    public Optional<AvailabilitySnapshot> get(UUID eventId) {
        String json = redis.opsForValue().get(PREFIX + eventId);

        if (json == null) {
            return Optional.empty();
        }

        try {
            /*
             * Deserializacja JSON-a z Redisa do obiektu AvailabilitySnapshot.
             */
            return Optional.of(objectMapper.readValue(json, AvailabilitySnapshot.class));
        } catch (JsonProcessingException ex) {
            /*
             * Jeśli wpis w cache jest uszkodzony albo pochodzi ze starej wersji formatu,
             * usuwamy go i traktujemy jako cache miss.
             *
             * Dla cache to zwykle bezpieczne: dane można odbudować z PostgreSQL.
             */
            evict(eventId);
            return Optional.empty();
        }
    }

    /**
     * Zapisuje snapshot dostępności do Redis z TTL.
     *
     * Snapshot dostępności powinien mieć krótki TTL, bo availableCapacity może
     * zmieniać się często przy tworzeniu i anulowaniu rezerwacji.
     *
     * Redis automatycznie usunie klucz po upływie TTL.
     */
    @Override
    public void put(AvailabilitySnapshot snapshot, Duration ttl) {
        try {
            redis.opsForValue().set(
                    PREFIX + snapshot.eventId(),
                    objectMapper.writeValueAsString(snapshot),
                    ttl
            );
        } catch (JsonProcessingException ex) {
            /*
             * Brak możliwości serializacji oznacza błąd techniczny aplikacji.
             */
            throw new IllegalStateException("Cannot serialize availability snapshot", ex);
        }
    }

    /**
     * Usuwa snapshot dostępności dla eventu.
     *
     * Powinno być wykonywane po zmianach wpływających na dostępność:
     *
     * - utworzeniu rezerwacji,
     * - anulowaniu rezerwacji,
     * - ręcznej zmianie puli miejsc.
     *
     * To jest klasyczna invalidacja cache.
     */
    @Override
    public void evict(UUID eventId) {
        redis.delete(PREFIX + eventId);
    }
}