package pl.jakubtworek.cloudarchitecture.service;

/** Raised when another request currently owns the same idempotency key. */
public class IdempotencyInProgressException extends RuntimeException {
    public IdempotencyInProgressException(String message) {
        super(message);
    }
}
