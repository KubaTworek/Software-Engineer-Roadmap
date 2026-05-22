package pl.jakubtworek.backend_engineering.stage_2.block_a.use_case.domain.model;

import java.util.UUID;

// Strongly typed identifier for the Order aggregate.
// It prevents mixing order IDs with unrelated string identifiers.
public record OrderId(String value) {

    public OrderId {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("OrderId cannot be empty");
        }
    }

    public static OrderId newId() {
        return new OrderId("O-" + UUID.randomUUID());
    }

    public static OrderId of(String value) {
        return new OrderId(value);
    }
}