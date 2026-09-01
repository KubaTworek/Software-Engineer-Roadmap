package pl.jakubtworek.backend_engineering.stage_3.block_a.implementation.overload;

import java.time.Duration;

public record RetryPolicy(
        int maxAttempts,
        Duration maximumAttemptTime,
        Duration parentReserve
) {

    public RetryPolicy {
        if (maxAttempts <= 0) {
            throw new IllegalArgumentException("maxAttempts must be positive");
        }
        if (maximumAttemptTime == null || maximumAttemptTime.isZero() || maximumAttemptTime.isNegative()) {
            throw new IllegalArgumentException("maximumAttemptTime must be positive");
        }
        if (parentReserve == null || parentReserve.isNegative()) {
            throw new IllegalArgumentException("parentReserve must not be negative");
        }
    }
}
