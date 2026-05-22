package pl.jakubtworek.backend_engineering.stage_2.block_b.domain.payments;

/**
 * Possible states of a payment.
 */
public enum PaymentStatus {
    PENDING,
    AUTHORIZED,
    FAILED
}