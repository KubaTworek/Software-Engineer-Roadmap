package pl.jakubtworek.backend_engineering.stage_2.block_a.ddd_tactic.domain.model;

// Possible lifecycle states of the Order aggregate.
public enum OrderStatus {
    DRAFT,
    PLACED,
    PAID,
    CANCELLED,
    SHIPPED
}