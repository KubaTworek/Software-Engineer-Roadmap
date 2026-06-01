package pl.jakubtworek.marketplace.ordering.domain;

public enum OrderStatus {
    PLACED,
    PENDING,
    PAYMENT_PENDING,
    STOCK_PENDING,
    CONFIRMED,
    FULFILLMENT_PENDING,
    COMPLETED,
    CANCELLED,
    REJECTED
}
