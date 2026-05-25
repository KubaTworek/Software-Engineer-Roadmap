package pl.jakubtworek.backend_engineering.stage_3.block_a.implementation.src.main.java.pl.jakubtworek.systemdesign.implementation.ratelimit;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Token bucket rate limiter.
 *
 * It allows short bursts up to bucket capacity while enforcing
 * an average refill rate over time.
 */
public class TokenBucketRateLimiter implements RateLimiter {

    private final double capacity;
    private final double refillTokensPerSecond;
    private final Map<String, Bucket> buckets = new ConcurrentHashMap<>();

    public TokenBucketRateLimiter(double capacity, double refillTokensPerSecond) {
        if (capacity <= 0) {
            throw new IllegalArgumentException("capacity must be positive");
        }

        if (refillTokensPerSecond <= 0) {
            throw new IllegalArgumentException("refillTokensPerSecond must be positive");
        }

        this.capacity = capacity;
        this.refillTokensPerSecond = refillTokensPerSecond;
    }

    @Override
    public RateLimitResult allow(String identity) {
        Bucket bucket = buckets.computeIfAbsent(identity, ignored -> new Bucket(capacity, Instant.now()));

        synchronized (bucket) {
            refill(bucket);

            if (bucket.tokens >= 1.0) {
                bucket.tokens -= 1.0;
                return RateLimitResult.isAllowed();
            }

            double missingTokens = 1.0 - bucket.tokens;
            long retryAfterMillis = (long) Math.ceil((missingTokens / refillTokensPerSecond) * 1000);

            return RateLimitResult.rejected(Duration.ofMillis(retryAfterMillis));
        }
    }

    private void refill(Bucket bucket) {
        Instant now = Instant.now();
        double elapsedSeconds = Duration.between(bucket.lastRefill, now).toNanos() / 1_000_000_000.0;

        bucket.tokens = Math.min(capacity, bucket.tokens + elapsedSeconds * refillTokensPerSecond);
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