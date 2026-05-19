package pl.jakubtworek.backend_systems_lab_stage_1.block_a.kotlin.domain.java;

// Enum is commonly used in Java to represent a fixed set of states.
// It works well when each state does not need different data.
public enum OrderStatus {
    CREATED,
    PAID,
    SHIPPED,
    COMPLETED,
    CANCELLED
}