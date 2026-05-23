package pl.jakubtworek.backend_engineering.stage_3.block_a.system_design.src.main.java.pl.jakubtworek.concepts;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Token bucket rate limiter.
 *
 * Formula:
 * tokens(t) = min(bucketCapacity, previousTokens + refillRatePerSecond * deltaSeconds)
 *
 * A request is allowed when at least one token is available.
 * This allows short bursts while preserving an average rate.
 */
public class TokenBucketRateLimiter implements RateLimiter {

    private final double bucketCapacity;
    private final double refillRatePerSecond;
    private final Map<String, Bucket> buckets = new ConcurrentHashMap<>();

    public TokenBucketRateLimiter(double bucketCapacity, double refillRatePerSecond) {
        if (bucketCapacity <= 0) {
            throw new IllegalArgumentException("bucketCapacity must be positive");
        }
        if (refillRatePerSecond <= 0) {
            throw new IllegalArgumentException("refillRatePerSecond must be positive");
        }
        this.bucketCapacity = bucketCapacity;
        this.refillRatePerSecond = refillRatePerSecond;
    }

    @Override
    public RateLimitDecision allow(String identity) {
        Bucket bucket = buckets.computeIfAbsent(identity, ignored -> new Bucket(bucketCapacity, Instant.now()));

        synchronized (bucket) {
            refill(bucket);

            if (bucket.tokens >= 1.0) {
                bucket.tokens -= 1.0;
                return RateLimitDecision.allowed();
            }

            double missingTokens = 1.0 - bucket.tokens;
            long retryMillis = (long) Math.ceil((missingTokens / refillRatePerSecond) * 1000);
            return RateLimitDecision.rejected(Duration.ofMillis(retryMillis));
        }
    }

    private void refill(Bucket bucket) {
        Instant now = Instant.now();
        double elapsedSeconds = Duration.between(bucket.lastRefill, now).toNanos() / 1_000_000_000.0;
        bucket.tokens = Math.min(bucketCapacity, bucket.tokens + elapsedSeconds * refillRatePerSecond);
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
