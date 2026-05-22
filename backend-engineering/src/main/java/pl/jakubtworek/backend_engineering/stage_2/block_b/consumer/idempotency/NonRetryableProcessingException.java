package pl.jakubtworek.backend_engineering.stage_2.block_b.consumer.idempotency;

/**
 * Exception representing a permanent processing failure.
 *
 * Examples: invalid event payload, unsupported schema version,
 * missing required business data.
 */
public class NonRetryableProcessingException extends RuntimeException {

    public NonRetryableProcessingException(String message, Throwable cause) {
        super(message, cause);
    }

    public NonRetryableProcessingException(String message) {
        super(message);
    }
}