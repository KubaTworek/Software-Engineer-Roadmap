package pl.jakubtworek.backend_systems_lab_stage_1.block_a.kotlin.domain.java;

import java.time.Instant;

// Success case with additional data.
public record PaymentSuccess(
        String transactionId,
        Instant paidAt
) implements PaymentResult {
}