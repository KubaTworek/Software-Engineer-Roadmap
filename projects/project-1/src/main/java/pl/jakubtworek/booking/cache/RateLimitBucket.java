package pl.jakubtworek.booking.cache;

import java.time.Instant;

/**
 * In-memory bucket używany przez prosty rate limiter.
 *
 * Ta klasa reprezentuje stan limitu dla jednego klienta, np.:
 *
 * - jednego adresu IP,
 * - jednego userId,
 * - jednego API key,
 * - jednej kombinacji userId + endpoint.
 *
 * Jest to implementacja edukacyjna typu fixed window:
 *
 * - bucket ma maksymalną liczbę tokenów,
 * - każdy request zużywa jeden token,
 * - po upływie okna czasowego tokeny są odnawiane,
 * - gdy tokeny się skończą, request jest odrzucany.
 *
 * To jest wersja in-memory, więc działa tylko w ramach jednej instancji aplikacji.
 * Jeśli uruchomisz kilka instancji monolitu, każda będzie miała osobny limit.
 * Dla limitu współdzielonego między instancjami lepszy jest Redis.
 */
public final class RateLimitBucket {

    /**
     * Maksymalna liczba tokenów w jednym oknie czasowym.
     *
     * Przykład:
     * maxTokens = 5 oznacza maksymalnie 5 requestów w jednym oknie.
     */
    private final int maxTokens;

    /**
     * Aktualna liczba pozostałych tokenów.
     *
     * To pole jest mutowalne, bo zmienia się przy każdym requestcie.
     */
    private int remainingTokens;

    /**
     * Moment, w którym obecne okno rate limitu się kończy.
     *
     * Po przekroczeniu tego czasu bucket zostaje odnowiony:
     * remainingTokens wraca do maxTokens.
     */
    private Instant resetAt;

    /**
     * Tworzy nowy bucket.
     *
     * Na początku klient dostaje pełną pulę tokenów.
     */
    public RateLimitBucket(int maxTokens, Instant resetAt) {
        this.maxTokens = maxTokens;
        this.remainingTokens = maxTokens;
        this.resetAt = resetAt;
    }

    /**
     * Próbuje zużyć jeden token.
     *
     * Metoda jest synchronized, ponieważ bucket może być używany równolegle
     * przez wiele requestów obsługiwanych przez różne wątki.
     *
     * Bez synchronized mogłoby dojść do race condition:
     *
     * - dwa wątki widzą remainingTokens = 1,
     * - oba uznają, że token jest dostępny,
     * - oba zmniejszają licznik,
     * - limit zostaje przekroczony.
     *
     * synchronized chroni sekcję krytyczną dla jednego bucketu.
     */
    public synchronized RateLimitDecision consume(Instant now, java.time.Duration window) {
        /*
         * Jeśli resetAt nie jest po aktualnym czasie, oznacza to,
         * że okno limitu już wygasło.
         *
         * Wtedy odnawiamy bucket:
         * - tokeny wracają do maxTokens,
         * - resetAt przesuwa się o długość nowego okna.
         */
        if (!resetAt.isAfter(now)) {
            remainingTokens = maxTokens;
            resetAt = now.plus(window);
        }

        /*
         * Jeśli tokenów już nie ma, request nie powinien zostać obsłużony.
         *
         * Zwracamy allowed = false oraz informację, kiedy limit się zresetuje.
         */
        if (remainingTokens <= 0) {
            return new RateLimitDecision(false, 0, resetAt);
        }

        /*
         * Zużywamy jeden token.
         */
        remainingTokens--;

        /*
         * Request mieści się w limicie.
         *
         * Zwracamy liczbę tokenów pozostałych po tym requestcie.
         */
        return new RateLimitDecision(true, remainingTokens, resetAt);
    }
}