package pl.jakubtworek.backend_engineering.stage_3.block_a.concepts.cache;

import java.time.Duration;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Adds random TTL jitter.
 *
 * TTL jitter reduces synchronized expiration of many keys,
 * which helps prevent cache stampede.
 */
public class TtlJitter {

    private final Duration maxJitter;
    private final long maxJitterMillis;

    public TtlJitter(Duration maxJitter) {
        if (maxJitter == null || maxJitter.isNegative()) {
            throw new IllegalArgumentException("maxJitter must be non-negative");
        }

        this.maxJitter = maxJitter;
        try {
            this.maxJitterMillis = maxJitter.toMillis();
        } catch (ArithmeticException exception) {
            throw new IllegalArgumentException("maxJitter is too large", exception);
        }
    }

    public Duration apply(Duration baseTtl) {
        if (baseTtl == null || baseTtl.isNegative() || baseTtl.isZero()) {
            throw new IllegalArgumentException("baseTtl must be positive");
        }

        if (maxJitter.isZero()) {
            return baseTtl;
        }

        long jitterMillis = maxJitterMillis == Long.MAX_VALUE
                ? ThreadLocalRandom.current().nextLong(Long.MAX_VALUE)
                : ThreadLocalRandom.current().nextLong(maxJitterMillis + 1);

        return baseTtl.plusMillis(jitterMillis);
    }
}
