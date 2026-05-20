package pl.jakubtworek.backend_engineering.stage_2.block_a.use_case.domain.model;

// Lifecycle state of the Order aggregate.
public enum OrderStatus {
    DRAFT,
    PLACED,
    PAID,
    CANCELLED
}