package pl.jakubtworek.booking.cache;

import org.springframework.context.annotation.Profile;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;

/**
 * Redisowa implementacja prostego rate limitera.
 *
 * Klasa jest aktywna tylko dla profilu:
 *
 * nosql-real
 *
 * Dzięki temu domyślne testy mogą korzystać z implementacji in-memory,
 * a Redis jest wymagany dopiero przy świadomym uruchomieniu profilu NoSQL.
 *
 * Ten rate limiter działa jak prosty fixed window counter:
 *
 * - dla danego clientKey tworzony jest klucz w Redisie,
 * - klucz ma TTL równy długości okna,
 * - każda próba zużywa jeden token,
 * - po wyczerpaniu tokenów request jest odrzucany,
 * - po wygaśnięciu klucza okno zaczyna się od nowa.
 *
 * To jest celowo prosta implementacja edukacyjna.
 */
@Component
@Profile("nosql-real")
public class RedisRateLimiterStore implements RateLimiterStore {

    /**
     * Prefix kluczy rate limitera w Redisie.
     *
     * Przykładowy klucz:
     *
     * rate-limit:192.168.0.10
     * rate-limit:user-123
     * rate-limit:api-key-xyz
     */
    private static final String PREFIX = "rate-limit:";

    /**
     * StringRedisTemplate służy do pracy z tekstowymi kluczami i wartościami.
     *
     * Licznik tokenów zapisujemy jako String, ale Redis potrafi wykonać na nim
     * operacje numeryczne typu DECR/INCR.
     */
    private final StringRedisTemplate redis;

    /**
     * Constructor injection.
     */
    public RedisRateLimiterStore(StringRedisTemplate redis) {
        this.redis = redis;
    }

    /**
     * Próbuje zużyć jeden token rate limitu dla podanego klienta.
     *
     * Parametry:
     *
     * - clientKey — identyfikator klienta, np. IP, userId albo API key,
     * - maxTokens — maksymalna liczba requestów w oknie,
     * - window — długość okna czasowego.
     *
     * Zwraca RateLimitDecision:
     *
     * - allowed = true, jeśli request mieści się w limicie,
     * - allowed = false, jeśli limit został przekroczony,
     * - remainingTokens — liczba pozostałych tokenów,
     * - resetAt — przybliżony czas resetu limitu.
     */
    @Override
    public RateLimitDecision consume(String clientKey, int maxTokens, Duration window) {
        String key = PREFIX + clientKey;

        /*
         * Tworzymy klucz tylko wtedy, gdy jeszcze nie istnieje.
         *
         * setIfAbsent działa jak Redis SETNX:
         * - jeśli klucz nie istnieje, ustawia wartość maxTokens i TTL,
         * - jeśli klucz istnieje, nie zmienia go.
         *
         * Dzięki TTL okno limitu automatycznie wygaśnie.
         */
        redis.opsForValue().setIfAbsent(key, String.valueOf(maxTokens), window);

        /*
         * Zużywamy jeden token.
         *
         * DECR jest operacją atomową po stronie Redisa.
         * To ważne, bo kilka requestów może równolegle próbować zużyć token.
         */
        Long value = redis.opsForValue().decrement(key);

        /*
         * Jeśli value == null, coś poszło nie tak po stronie Redisa.
         *
         * Jeśli value < 0, oznacza to, że tokeny zostały przekroczone.
         *
         * Przykład:
         * - maxTokens = 5,
         * - kolejne wartości po DECR: 4, 3, 2, 1, 0, -1.
         *
         * Przy -1 request powinien zostać odrzucony.
         */
        if (value == null || value < 0) {
            if (value != null && value < 0) {
                /*
                 * Cofamy decrement, żeby licznik nie spadał coraz niżej
                 * przy kolejnych odrzuconych requestach.
                 *
                 * To nie jest idealny algorytm, ale wystarcza jako przykład.
                 */
                redis.opsForValue().increment(key);
            }

            /*
             * To resetAt jest przybliżone.
             *
             * Dokładniejsza implementacja powinna odczytać TTL klucza z Redisa
             * i policzyć resetAt = now + ttl.
             */
            Instant resetAt = Instant.now().plus(window);

            return new RateLimitDecision(false, 0, resetAt);
        }

        /*
         * Request mieści się w limicie.
         *
         * value oznacza liczbę tokenów pozostałych po zużyciu jednego.
         */
        return new RateLimitDecision(
                true,
                value.intValue(),
                Instant.now().plus(window)
        );
    }
}