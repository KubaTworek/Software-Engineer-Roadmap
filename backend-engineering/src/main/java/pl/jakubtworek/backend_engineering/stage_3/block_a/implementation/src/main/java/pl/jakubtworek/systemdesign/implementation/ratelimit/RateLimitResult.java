package pl.jakubtworek.backend_engineering.stage_3.block_a.implementation.src.main.java.pl.jakubtworek.systemdesign.implementation.ratelimit;

import java.time.Duration;

/**
 * Result of a rate limiting decision.
 *
 * If isAllowed is false, an HTTP layer should usually return:
 * 429 Too Many Requests
 * Retry-After: retryAfter seconds
 */
public record RateLimitResult(
        boolean allowed,
        Duration retryAfter
) {
    public static RateLimitResult isAllowed() {
        return new RateLimitResult(true, Duration.ZERO);
    }

    public static RateLimitResult rejected(Duration retryAfter) {
        return new RateLimitResult(false, retryAfter);
    }
}