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