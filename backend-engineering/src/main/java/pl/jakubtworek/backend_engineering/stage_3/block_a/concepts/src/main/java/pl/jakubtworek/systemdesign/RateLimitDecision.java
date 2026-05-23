package pl.jakubtworek.backend_engineering.stage_3.block_a.concepts.src.main.java.pl.jakubtworek.systemdesign;

import java.time.Duration;

/**
 * Represents the result of a rate limiting decision.
 *
 * When allowed is false, an HTTP API should usually return 429 Too Many Requests
 * and may include a Retry-After header derived from retryAfter.
 */
public record RateLimitDecision(
        boolean allowed,
        Duration retryAfter
) {
    public static RateLimitDecision allowed() {
        return new RateLimitDecision(true, Duration.ZERO);
    }

    public static RateLimitDecision rejected(Duration retryAfter) {
        return new RateLimitDecision(false, retryAfter);
    }
}
