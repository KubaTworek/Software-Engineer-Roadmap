package pl.jakubtworek.booking.cache;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.Profile;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * Redisowa implementacja store'a dla tymczasowych holdów rezerwacyjnych.
 *
 * Ta klasa jest aktywna tylko dla profilu:
 *
 * nosql-real
 *
 * Dzięki temu zwykłe testy i domyślne uruchomienie aplikacji nie muszą mieć
 * działającego Redisa. W testach można użyć implementacji in-memory.
 *
 * Hold rezerwacyjny to krótkotrwały wpis:
 *
 * - kto próbuje zarezerwować,
 * - na jaki event,
 * - kiedy hold powstał,
 * - kiedy wygasa.
 *
 * Redis dobrze pasuje do takiego przypadku, bo wspiera TTL na kluczach.
 */
@Component
@Profile("nosql-real")
public class RedisReservationHoldStore implements ReservationHoldStore {

    /**
     * Prefix kluczy w Redisie.
     *
     * Dzięki prefixowi łatwiej rozróżnić typy danych w Redisie.
     *
     * Przykładowy klucz:
     *
     * reservation-hold:550e8400-e29b-41d4-a716-446655440000
     */
    private static final String PREFIX = "reservation-hold:";

    /**
     * StringRedisTemplate operuje na kluczach i wartościach tekstowych.
     *
     * Ponieważ ReservationHold jest obiektem Javy, zapisujemy go jako JSON.
     */
    private final StringRedisTemplate redis;

    /**
     * ObjectMapper służy do serializacji/deserializacji ReservationHold do/z JSON.
     *
     * To proste rozwiązanie edukacyjne. Produkcyjnie warto jasno kontrolować format,
     * wersjonowanie payloadu i obsługę kompatybilności.
     */
    private final ObjectMapper objectMapper;

    /**
     * Constructor injection.
     */
    public RedisReservationHoldStore(StringRedisTemplate redis, ObjectMapper objectMapper) {
        this.redis = redis;
        this.objectMapper = objectMapper;
    }

    /**
     * Tworzy tymczasowy hold i zapisuje go w Redisie z TTL.
     *
     * Redis sam usunie klucz po upływie ttl.
     *
     * To eliminuje potrzebę ręcznego sprzątania wygasłych holdów przez scheduler.
     */
    @Override
    public ReservationHold create(UUID eventId, String customerEmail, Duration ttl) {
        Instant now = Instant.now();

        ReservationHold hold = new ReservationHold(
                UUID.randomUUID(),
                eventId,
                customerEmail,
                now,
                now.plus(ttl)
        );

        try {
            /*
             * SET key value EX ttl
             *
             * StringRedisTemplate ustawi wartość oraz czas życia klucza.
             * Po wygaśnięciu Redis automatycznie usunie wpis.
             */
            redis.opsForValue().set(
                    PREFIX + hold.holdId(),
                    objectMapper.writeValueAsString(hold),
                    ttl
            );
        } catch (JsonProcessingException ex) {
            /*
             * Jeśli nie umiemy zapisać holda do JSON, to jest błąd techniczny
             * aplikacji, nie błąd biznesowy użytkownika.
             */
            throw new IllegalStateException("Cannot serialize reservation hold", ex);
        }

        return hold;
    }

    /**
     * Szuka holda po ID.
     *
     * Jeśli Redis zwróci null, oznacza to zwykle:
     *
     * - hold nigdy nie istniał,
     * - albo wygasł przez TTL,
     * - albo został usunięty.
     *
     * W każdym z tych przypadków zwracamy Optional.empty().
     */
    @Override
    public Optional<ReservationHold> find(UUID holdId) {
        String json = redis.opsForValue().get(PREFIX + holdId);

        if (json == null) {
            return Optional.empty();
        }

        try {
            return Optional.of(objectMapper.readValue(json, ReservationHold.class));
        } catch (JsonProcessingException ex) {
            /*
             * Jeśli wpis w Redisie jest uszkodzony albo ma niekompatybilny format,
             * usuwamy go i traktujemy jako brak holda.
             *
             * To jest rozsądne dla cache/tymczasowych danych, ale dla danych
             * krytycznych nie powinno się ich po prostu kasować bez audytu.
             */
            redis.delete(PREFIX + holdId);
            return Optional.empty();
        }
    }

    /**
     * Usuwa wygasłe holdy.
     *
     * W implementacji Redis ta metoda nic nie robi, bo wygasanie obsługuje Redis
     * przez TTL na kluczach.
     *
     * Zwracamy 0, ponieważ nie wykonaliśmy żadnego ręcznego usunięcia.
     *
     * Ta metoda może mieć sens w implementacji in-memory, gdzie trzeba okresowo
     * sprzątać wygasłe wpisy.
     */
    @Override
    public int removeExpired() {
        return 0;
    }
}