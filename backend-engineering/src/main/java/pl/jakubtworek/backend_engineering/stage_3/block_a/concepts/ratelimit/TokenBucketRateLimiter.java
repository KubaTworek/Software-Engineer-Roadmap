package pl.jakubtworek.backend_engineering.stage_3.block_a.concepts.ratelimit;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Token bucket rate limiter.
 *
 * tokens(t) = min(capacity, previousTokens + refillRate * deltaSeconds)
 */
public class TokenBucketRateLimiter implements RateLimiter {

    private final double capacity;
    private final double refillTokensPerSecond;
    private final Clock clock;
    private final Map<String, Bucket> buckets = new ConcurrentHashMap<>();

    public TokenBucketRateLimiter(double capacity, double refillTokensPerSecond) {
        this(capacity, refillTokensPerSecond, Clock.systemUTC());
    }

    public TokenBucketRateLimiter(double capacity, double refillTokensPerSecond, Clock clock) {
        if (!Double.isFinite(capacity) || capacity <= 0) throw new IllegalArgumentException("capacity must be finite and positive");
        if (!Double.isFinite(refillTokensPerSecond) || refillTokensPerSecond <= 0) throw new IllegalArgumentException("refillTokensPerSecond must be finite and positive");
        if (clock == null) throw new IllegalArgumentException("clock is required");

        this.capacity = capacity;
        this.refillTokensPerSecond = refillTokensPerSecond;
        this.clock = clock;
    }

    @Override
    public RateLimitDecision allow(String identity) {
        if (identity == null || identity.isBlank()) {
            throw new IllegalArgumentException("identity is required");
        }
        Bucket bucket = buckets.computeIfAbsent(
                identity,
                ignored -> new Bucket(capacity, clock.instant())
        );

        synchronized (bucket) {
            refill(bucket);

            if (bucket.tokens >= 1.0) {
                bucket.tokens -= 1.0;
                return RateLimitDecision.permitted();
            }

            double missingTokens = 1.0 - bucket.tokens;
            long retryAfterMillis = (long) Math.ceil(
                    (missingTokens / refillTokensPerSecond) * 1000
            );

            return RateLimitDecision.rejected(Duration.ofMillis(retryAfterMillis));
        }
    }

    private void refill(Bucket bucket) {
        Instant now = clock.instant();
        double elapsedSeconds = Math.max(
                0,
                Duration.between(bucket.lastRefill, now).toNanos() / 1_000_000_000.0
        );

        bucket.tokens = Math.min(
                capacity,
                bucket.tokens + elapsedSeconds * refillTokensPerSecond
        );

        bucket.lastRefill = now;
    }

    private static final class Bucket {
        private double tokens;
        private Instant lastRefill;

        private Bucket(double tokens, Instant lastRefill) {
            this.tokens = tokens;
            this.lastRefill = lastRefill;
        }
    }
}
