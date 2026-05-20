package pl.jakubtworek.backend_engineering.stage_2.block_a.ddd_tactic.domain.model;

import java.util.Objects;

// Value object representing product quantity.
// It prevents invalid quantity values from entering the domain model.
public final class Quantity implements ValueObject {

    private final int value;

    private Quantity(int value) {
        if (value <= 0) {
            throw new IllegalArgumentException("Quantity must be greater than zero");
        }
        this.value = value;
    }

    public static Quantity of(int value) {
        return new Quantity(value);
    }

    public int value() {
        return value;
    }

    @Override
    public boolean equals(Object other) {
        if (!(other instanceof Quantity that)) {
            return false;
        }
        return this.value == that.value;
    }

    @Override
    public int hashCode() {
        return Objects.hash(value);
    }
}