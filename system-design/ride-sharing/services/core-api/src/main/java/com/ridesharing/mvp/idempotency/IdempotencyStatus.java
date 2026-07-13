package com.ridesharing.mvp.idempotency;

public enum IdempotencyStatus {
    PROCESSING,
    COMPLETED,
    FAILED
}
