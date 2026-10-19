package com.example.paymentsystem.idempotency;

public enum IdempotencyStatus {
    PROCESSING,
    COMPLETED,
    UNKNOWN,
    FAILED_RETRYABLE
}
