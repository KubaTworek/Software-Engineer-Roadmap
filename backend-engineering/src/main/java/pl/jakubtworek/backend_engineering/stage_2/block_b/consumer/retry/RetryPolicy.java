package pl.jakubtworek.backend_engineering.stage_2.block_b.consumer.retry;

/**
 * Configuration for retry behavior.
 *
 * maxAttempts defines how many times the consumer should try before giving up
 * and sending the message to a dead-letter topic.
 */
public record RetryPolicy(
        int maxAttempts,
        RetryBackoffStrategy backoffStrategy
) {
    public RetryPolicy {
        if (maxAttempts <= 0) {
            throw new IllegalArgumentException("Max attempts must be positive");
        }
        if (backoffStrategy == null) {
            throw new IllegalArgumentException("Backoff strategy cannot be null");
        }
    }
}
