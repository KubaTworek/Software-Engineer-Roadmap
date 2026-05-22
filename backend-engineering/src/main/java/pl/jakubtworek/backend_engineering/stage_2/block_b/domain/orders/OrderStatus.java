package pl.jakubtworek.backend_engineering.stage_2.block_b.domain.orders;

/**
 * Possible states of an order.
 *
 * Intermediate states are normal in eventually consistent systems.
 * For example, an order may remain in PENDING_PAYMENT until Payment Service
 * processes the payment and emits either PaymentAuthorized or PaymentFailed.
 */
public enum OrderStatus {
    PENDING_PAYMENT,
    CONFIRMED,
    CANCELLED,
    SHIPPED
}