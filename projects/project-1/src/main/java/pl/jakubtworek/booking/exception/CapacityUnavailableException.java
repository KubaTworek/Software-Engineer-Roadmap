package pl.jakubtworek.booking.exception;

public class CapacityUnavailableException extends RuntimeException {
    public CapacityUnavailableException(String message) {
        super(message);
    }
}
