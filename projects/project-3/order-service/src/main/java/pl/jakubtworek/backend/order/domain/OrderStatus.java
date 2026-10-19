package pl.jakubtworek.backend.order.domain;

public enum OrderStatus {
    PENDING,
    PAYMENT_IN_PROGRESS,
    PAYMENT_UNKNOWN,
    RESERVATION_CONFIRMATION_PENDING,
    COMPENSATION_PENDING,
    MANUAL_REVIEW,
    PAID,
    FAILED
}
