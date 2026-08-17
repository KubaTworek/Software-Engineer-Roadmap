package com.example.paymentsystem.idempotency;

public class IdempotencyConflictException extends RuntimeException {
    public IdempotencyConflictException() {
        super("Idempotency-Key was already used with a different payload");
    }
}
