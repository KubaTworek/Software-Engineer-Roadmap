package pl.jakubtworek.backend_engineering.stage_3.block_a.implementation.src.main.java.pl.jakubtworek.systemdesign.implementation.remote;

/**
 * Raised when a remote call exceeds its request timeout.
 */
public class RemoteCallTimeoutException extends RuntimeException {

    public RemoteCallTimeoutException(String message) {
        super(message);
    }
}