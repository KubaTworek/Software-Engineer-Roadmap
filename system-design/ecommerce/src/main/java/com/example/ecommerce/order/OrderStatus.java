package com.example.ecommerce.order;

public enum OrderStatus {
    PENDING_PAYMENT,
    PAID,
    PAYMENT_FAILED,
    PROCESSING,
    SHIPPED,
    DELIVERED,
    CANCELLED,
    REFUNDED
}
