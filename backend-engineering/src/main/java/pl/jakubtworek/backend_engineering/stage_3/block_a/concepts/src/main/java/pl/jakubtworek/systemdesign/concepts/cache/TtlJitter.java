package pl.jakubtworek.backend_engineering.stage_3.block_a.concepts.src.main.java.pl.jakubtworek.systemdesign.concepts.cache;

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

    public TtlJitter(Duration maxJitter) {
        if (maxJitter == null || maxJitter.isNegative()) {
            throw new IllegalArgumentException("maxJitter must be non-negative");
        }

        this.maxJitter = maxJitter;
    }

    public Duration apply(Duration baseTtl) {
        if (baseTtl == null || baseTtl.isNegative() || baseTtl.isZero()) {
            throw new IllegalArgumentException("baseTtl must be positive");
        }

        if (maxJitter.isZero()) {
            return baseTtl;
        }

        long jitterMillis = ThreadLocalRandom.current()
                .nextLong(maxJitter.toMillis() + 1);

        return baseTtl.plusMillis(jitterMillis);
    }
}