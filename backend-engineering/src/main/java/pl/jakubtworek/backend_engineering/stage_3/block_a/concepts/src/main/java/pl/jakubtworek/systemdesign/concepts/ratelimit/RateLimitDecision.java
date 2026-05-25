package pl.jakubtworek.backend_engineering.stage_3.block_a.concepts.src.main.java.pl.jakubtworek.systemdesign.concepts.ratelimit;

import java.time.Duration;

/**
 * Result of rate limiting.
 *
 * If rejected, HTTP adapters should usually return:
 * 429 Too Many Requests
 * Retry-After: retryAfter
 */
public record RateLimitDecision(
        boolean allowed,
        Duration retryAfter
) {
    public static RateLimitDecision isAllowed() {
        return new RateLimitDecision(true, Duration.ZERO);
    }

    public static RateLimitDecision rejected(Duration retryAfter) {
        return new RateLimitDecision(false, retryAfter);
    }
}