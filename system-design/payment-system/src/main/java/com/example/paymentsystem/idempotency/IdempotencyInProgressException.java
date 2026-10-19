package com.example.paymentsystem.idempotency;

public class IdempotencyInProgressException extends RuntimeException {
    public IdempotencyInProgressException() {
        super("A request with this Idempotency-Key is already being processed");
    }
}
