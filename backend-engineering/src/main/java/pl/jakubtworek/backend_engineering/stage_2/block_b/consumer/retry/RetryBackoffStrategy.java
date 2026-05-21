package pl.jakubtworek.backend_engineering.stage_2.block_b.consumer.retry;

import java.time.Duration;

/**
 * Defines a strategy for calculating delay before the next retry attempt.
 *
 * Retry delays should usually grow over time to avoid overwhelming
 * an already degraded system.
 */
public interface RetryBackoffStrategy {

    /**
     * Calculates delay for the given attempt number.
     *
     * Attempt numbers usually start at 1.
     */
    Duration calculateDelay(int attemptNumber);
}