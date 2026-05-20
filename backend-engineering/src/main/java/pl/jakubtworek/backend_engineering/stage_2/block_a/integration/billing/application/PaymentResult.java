package pl.jakubtworek.backend_engineering.stage_2.block_a.integration.billing.application;

// Result returned by the payment gateway.
public record PaymentResult(
        boolean successful,
        String paymentId,
        String failureReason
) {
}