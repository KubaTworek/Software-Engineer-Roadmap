package pl.jakubtworek.backend_engineering.stage_2.block_a.ddd_tactic.domain.model;

import java.util.Objects;

// Strongly typed identifier for a product.
// Product is another aggregate, so Order keeps only the product ID.
public final class ProductId implements ValueObject {

    private final String value;

    private ProductId(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("ProductId cannot be empty");
        }
        this.value = value;
    }

    public static ProductId of(String value) {
        return new ProductId(value);
    }

    public String value() {
        return value;
    }

    @Override
    public boolean equals(Object other) {
        if (!(other instanceof ProductId that)) {
            return false;
        }
        return Objects.equals(this.value, that.value);
    }

    @Override
    public int hashCode() {
        return Objects.hash(value);
    }
}