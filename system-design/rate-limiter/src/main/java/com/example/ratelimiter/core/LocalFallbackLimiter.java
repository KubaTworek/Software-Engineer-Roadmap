package com.example.ratelimiter.core;

import com.example.ratelimiter.config.RateLimiterProperties;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * LocalFallbackLimiter to awaryjny limiter działający lokalnie w pamięci aplikacji.
 *
 * Jest używany wtedy, gdy Redis jest niedostępny, a strategia awarii reguły
 * lub globalna strategia aplikacji to LOCAL_FALLBACK.
 *
 * Normalny flow:
 *
 * RateLimiterEngine
 *   -> RedisTokenBucketLimiter
 *   -> Redis działa
 *   -> decyzja z Redisa
 *
 * Awaryjny flow:
 *
 * RateLimiterEngine
 *   -> RedisTokenBucketLimiter
 *   -> Redis rzuca wyjątek
 *   -> LocalFallbackLimiter
 *   -> decyzja lokalna
 *
 * Ważne ograniczenie:
 * ten limiter działa tylko w ramach jednej instancji aplikacji.
 * Jeśli aplikacja ma 5 instancji, każda z nich ma własne lokalne liczniki.
 *
 * To oznacza, że LOCAL_FALLBACK nie daje globalnej spójności,
 * ale pozwala zachować podstawową ochronę backendu podczas awarii Redisa.
 */
@Component
public class LocalFallbackLimiter {

    /**
     * Konfiguracja Rate Limitera.
     *
     * Tutaj używana głównie do odczytu ustawień lokalnego fallbacku:
     * - defaultLimit,
     * - defaultWindowSeconds.
     */
    private final RateLimiterProperties properties;

    /**
     * Lokalny storage liczników.
     *
     * Klucz mapy zawiera:
     * - id reguły,
     * - principal requestu,
     * - numer aktualnego okna czasowego.
     *
     * Przykład:
     *
     * user-limit:user:123:48291381
     *
     * ConcurrentHashMap jest potrzebny, bo wiele requestów może być
     * obsługiwanych równolegle przez różne wątki aplikacji.
     */
    private final Map<String, WindowCounter> counters = new ConcurrentHashMap<>();

    public LocalFallbackLimiter(RateLimiterProperties properties) {
        this.properties = properties;
    }

    /**
     * Zużywa jeden request/koszt w lokalnym fallback limiterze.
     *
     * Ten fallback używa prostego Fixed Window Counter:
     *
     * - dzielimy czas na okna, np. 60 sekund,
     * - dla każdego okna trzymamy licznik,
     * - jeśli licznik przekroczy limit, request jest blokowany.
     *
     * To jest prostsze niż Token Bucket, ale wystarczające jako tryb awaryjny.
     */
    public RuleDecision consume(RateLimiterProperties.Rule rule, RequestContext ctx, String reason) {
        /*
         * Długość okna fallbacku, np. 60 sekund.
         *
         * To nie musi być taka sama wartość jak w Token Bucket.
         * Fallback ma być prosty i konserwatywny.
         */
        long windowSeconds = properties.getLocalFallback().getDefaultWindowSeconds();

        /*
         * Limit fallbacku bierzemy jako minimum z:
         * - capacity reguły,
         * - globalnego defaultLimit dla fallbacku.
         *
         * Dzięki temu awaryjny limiter nie pozwala na więcej niż reguła,
         * ale może dodatkowo obniżyć limit, żeby chronić system podczas awarii.
         */
        long limit = Math.min(
                rule.getCapacity(),
                properties.getLocalFallback().getDefaultLimit()
        );

        /*
         * Czas requestu bierzemy z RequestContext.
         *
         * To utrzymuje spójność z resztą systemu,
         * gdzie timestamp jest częścią kontekstu requestu.
         */
        long nowSec = Instant.ofEpochMilli(ctx.timestampMs()).getEpochSecond();

        /*
         * Wyliczamy numer okna czasowego.
         *
         * Przykład dla windowSeconds=60:
         * wszystkie requesty z tej samej minuty trafią do tego samego okna.
         */
        long window = nowSec / windowSeconds;

        /*
         * Klucz lokalnego licznika.
         *
         * Rozdzielamy liczniki per:
         * - ruleId,
         * - principalKey,
         * - window.
         *
         * Dzięki temu różne reguły i różni klienci nie mieszają liczników.
         */
        String key = rule.getId() + ":" + ctx.principalKey() + ":" + window;

        /*
         * Atomowo tworzymy licznik, jeśli go jeszcze nie ma,
         * a potem zwiększamy go o 1.
         *
         * AtomicLong zapewnia bezpieczne zwiększanie licznika
         * przy równoległych requestach.
         *
         * Uwaga:
         * ta implementacja zlicza requesty jako koszt 1.
         * Nie uwzględnia rule.getCost().
         */
        long value = counters
                .computeIfAbsent(key, k -> new WindowCounter(window))
                .counter
                .incrementAndGet();

        /*
         * Request jest dozwolony, jeśli licznik w aktualnym oknie
         * nie przekroczył limitu.
         */
        boolean allowed = value <= limit;

        /*
         * Jeśli request został zablokowany, retryAfter wskazuje,
         * ile sekund zostało do końca obecnego okna.
         *
         * Po rozpoczęciu nowego okna licznik będzie liczony od zera,
         * bo key zawiera numer okna.
         */
        long retry = allowed
                ? 0
                : ((window + 1) * windowSeconds) - nowSec;

        /*
         * Zwracamy decyzję w tym samym formacie co Redis limiter.
         *
         * source = "local-fallback" pozwala później w logach/debugu zobaczyć,
         * że decyzja nie pochodziła z Redisa.
         *
         * reason zwykle mówi, dlaczego weszliśmy w fallback,
         * np. "REDIS_UNAVAILABLE".
         */
        return new RuleDecision(
                rule.getId(),
                allowed,
                limit,
                Math.max(0, limit - value),
                retry,
                "local-fallback",
                reason
        );
    }

    /**
     * Licznik dla jednego okna czasowego.
     *
     * Record przechowuje:
     * - numer okna,
     * - licznik requestów w tym oknie.
     *
     * AtomicLong jest użyty, bo requesty są obsługiwane wielowątkowo.
     */
    private record WindowCounter(long window, AtomicLong counter) {

        WindowCounter(long window) {
            this(window, new AtomicLong());
        }
    }
}