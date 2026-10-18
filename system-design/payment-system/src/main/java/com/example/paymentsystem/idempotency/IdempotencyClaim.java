package com.example.paymentsystem.idempotency;

public record IdempotencyClaim(Outcome outcome, IdempotencyRecord record) {
    public enum Outcome { ACQUIRED, REPLAY, IN_PROGRESS }
}
