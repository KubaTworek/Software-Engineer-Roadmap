package pl.jakubtworek.backend_engineering.stage_3.block_a.concepts.resilience;

/**
 * Raised when a remote call exceeds its timeout.
 */
public class RemoteTimeoutException extends RuntimeException {

    public RemoteTimeoutException(String message) {
        super(message);
    }
}