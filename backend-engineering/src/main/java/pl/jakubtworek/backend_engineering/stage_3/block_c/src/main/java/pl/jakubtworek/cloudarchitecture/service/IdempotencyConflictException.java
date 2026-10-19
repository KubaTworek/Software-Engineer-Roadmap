package pl.jakubtworek.cloudarchitecture.service;

/** Raised when one idempotency key is reused for a different request payload. */
public class IdempotencyConflictException extends RuntimeException {
    public IdempotencyConflictException(String message) {
        super(message);
    }
}
