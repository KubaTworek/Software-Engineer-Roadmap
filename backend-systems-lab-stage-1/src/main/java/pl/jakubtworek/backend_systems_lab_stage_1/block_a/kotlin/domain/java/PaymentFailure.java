package pl.jakubtworek.backend_systems_lab_stage_1.block_a.kotlin.domain.java;

// Failure case with different data.
public record PaymentFailure(
        String reason
) implements PaymentResult {
}