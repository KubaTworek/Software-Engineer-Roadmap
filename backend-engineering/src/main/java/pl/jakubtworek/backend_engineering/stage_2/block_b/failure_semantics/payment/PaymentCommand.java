package pl.jakubtworek.backend_engineering.stage_2.block_b.failure_semantics.payment;

import java.math.BigDecimal;
import java.util.Objects;
import java.util.UUID;

/** A retryable payment command whose idempotency key identifies one business operation. */
public record PaymentCommand(UUID idempotencyKey, String orderId, BigDecimal amount) {

    public PaymentCommand {
        Objects.requireNonNull(idempotencyKey, "idempotencyKey must not be null");
        if (orderId == null || orderId.isBlank()) {
            throw new IllegalArgumentException("orderId must not be blank");
        }
        Objects.requireNonNull(amount, "amount must not be null");
        if (amount.signum() <= 0) {
            throw new IllegalArgumentException("amount must be positive");
        }
    }
}
