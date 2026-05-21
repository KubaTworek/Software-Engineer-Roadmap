package pl.jakubtworek.backend_engineering.stage_2.block_b.consumer.retry;

/**
 * Exception thrown when retry processing is interrupted.
 */
public class RetryInterruptedException extends RuntimeException {

    public RetryInterruptedException(String message, Throwable cause) {
        super(message, cause);
    }
}