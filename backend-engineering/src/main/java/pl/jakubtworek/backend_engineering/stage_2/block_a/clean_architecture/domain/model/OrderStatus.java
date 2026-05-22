package pl.jakubtworek.backend_engineering.stage_2.block_a.clean_architecture.domain.model;

// Domain enum representing the lifecycle of an order.
public enum OrderStatus {
    DRAFT,
    PLACED,
    PAID,
    CANCELLED
}