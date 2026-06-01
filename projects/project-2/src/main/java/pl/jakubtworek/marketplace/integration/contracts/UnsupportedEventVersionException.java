package pl.jakubtworek.marketplace.integration.contracts;

public class UnsupportedEventVersionException extends RuntimeException {
    public UnsupportedEventVersionException(String message) {
        super(message);
    }

    public UnsupportedEventVersionException(String message, Throwable cause) {
        super(message, cause);
    }
}
