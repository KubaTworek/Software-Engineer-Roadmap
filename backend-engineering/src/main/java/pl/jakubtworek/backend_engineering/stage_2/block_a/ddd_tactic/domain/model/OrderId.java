package pl.jakubtworek.backend_engineering.stage_2.block_a.ddd_tactic.domain.model;

import java.util.Objects;
import java.util.UUID;

// Strongly typed identifier for the Order aggregate.
// Using a dedicated type prevents mixing order IDs with other IDs.
public final class OrderId implements ValueObject {

    private final String value;

    private OrderId(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("OrderId cannot be empty");
        }
        this.value = value;
    }

    public static OrderId newId() {
        return new OrderId("O-" + UUID.randomUUID());
    }

    public static OrderId of(String value) {
        return new OrderId(value);
    }

    public String value() {
        return value;
    }

    @Override
    public boolean equals(Object other) {
        if (!(other instanceof OrderId that)) {
            return false;
        }
        return Objects.equals(this.value, that.value);
    }

    @Override
    public int hashCode() {
        return Objects.hash(value);
    }
}