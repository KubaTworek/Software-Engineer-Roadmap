package pl.jakubtworek.backend_engineering.stage_2.block_b.consumer.idempotency;

/**
 * Exception representing a temporary processing failure.
 *
 * Examples: network timeout, temporary database unavailability,
 * temporary failure of an external payment provider.
 */
public class RetryableProcessingException extends RuntimeException {

    public RetryableProcessingException(String message, Throwable cause) {
        super(message, cause);
    }

    public RetryableProcessingException(String message) {
        super(message);
    }
}