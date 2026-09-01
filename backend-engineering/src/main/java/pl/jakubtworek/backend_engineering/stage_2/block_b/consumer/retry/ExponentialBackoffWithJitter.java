package pl.jakubtworek.backend_engineering.stage_2.block_b.consumer.retry;

import java.time.Duration;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Exponential backoff strategy with jitter.
 *
 * Backoff increases delay after every failed attempt.
 * Jitter adds randomness so multiple consumers do not retry at exactly the same time.
 */
public class ExponentialBackoffWithJitter implements RetryBackoffStrategy {

    private final Duration initialDelay;
    private final Duration maxDelay;
    private final double multiplier;
    private final double jitterRatio;

    public ExponentialBackoffWithJitter(
            Duration initialDelay,
            Duration maxDelay,
            double multiplier,
            double jitterRatio
    ) {
        if (initialDelay == null || initialDelay.isNegative()) {
            throw new IllegalArgumentException("Initial delay cannot be negative");
        }
        if (maxDelay == null || maxDelay.isNegative() || maxDelay.compareTo(initialDelay) < 0) {
            throw new IllegalArgumentException("Max delay cannot be smaller than initial delay");
        }
        if (!Double.isFinite(multiplier) || multiplier < 1.0) {
            throw new IllegalArgumentException("Multiplier must be finite and at least 1.0");
        }
        if (!Double.isFinite(jitterRatio) || jitterRatio < 0.0 || jitterRatio > 1.0) {
            throw new IllegalArgumentException("Jitter ratio must be between 0.0 and 1.0");
        }

        this.initialDelay = initialDelay;
        this.maxDelay = maxDelay;
        this.multiplier = multiplier;
        this.jitterRatio = jitterRatio;
    }

    /**
     * Calculates delay using exponential growth and random jitter.
     *
     * Example:
     * attempt 1 -> around 1 second
     * attempt 2 -> around 2 seconds
     * attempt 3 -> around 4 seconds
     */
    @Override
    public Duration calculateDelay(int attemptNumber) {
        if (attemptNumber <= 0) {
            throw new IllegalArgumentException("Attempt number must be positive");
        }

        double exponentialDelayMillis = initialDelay.toMillis()
                * Math.pow(multiplier, attemptNumber - 1);

        long cappedDelayMillis = Math.min(
                (long) exponentialDelayMillis,
                maxDelay.toMillis()
        );

        long jitterMillis = (long) (cappedDelayMillis * jitterRatio);

        long randomizedDelayMillis = cappedDelayMillis
                + ThreadLocalRandom.current().nextLong(-jitterMillis, jitterMillis + 1);

        return Duration.ofMillis(Math.max(0, randomizedDelayMillis));
    }
}
