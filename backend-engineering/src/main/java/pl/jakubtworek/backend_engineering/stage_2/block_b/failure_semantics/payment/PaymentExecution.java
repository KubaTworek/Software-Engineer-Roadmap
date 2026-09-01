package pl.jakubtworek.backend_engineering.stage_2.block_b.failure_semantics.payment;

import java.util.UUID;
import java.util.Objects;

public record PaymentExecution(
        PaymentExecutionStatus status,
        int attempts,
        UUID idempotencyKey,
        String detail
) {
    public PaymentExecution {
        Objects.requireNonNull(status, "status must not be null");
        Objects.requireNonNull(idempotencyKey, "idempotencyKey must not be null");
        if (attempts <= 0) {
            throw new IllegalArgumentException("attempts must be positive");
        }
        if (detail == null || detail.isBlank()) {
            throw new IllegalArgumentException("detail must not be blank");
        }
    }
}
