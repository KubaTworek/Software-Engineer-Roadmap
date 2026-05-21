package pl.jakubtworek.backend_engineering.stage_2.block_b.consumer.deduplication;

/**
 * Exception thrown when deduplication storage cannot be accessed.
 *
 * This should usually be treated as a retryable infrastructure failure.
 */
public class DeduplicationException extends RuntimeException {

    public DeduplicationException(String message, Throwable cause) {
        super(message, cause);
    }
}