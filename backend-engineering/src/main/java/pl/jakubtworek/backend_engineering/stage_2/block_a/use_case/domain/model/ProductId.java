package pl.jakubtworek.backend_engineering.stage_2.block_a.use_case.domain.model;

// Strongly typed identifier for a product.
// Product is a separate concept and should not be represented as a raw string everywhere.
public record ProductId(String value) {

    public ProductId {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("ProductId cannot be empty");
        }
    }

    public static ProductId of(String value) {
        return new ProductId(value);
    }
}