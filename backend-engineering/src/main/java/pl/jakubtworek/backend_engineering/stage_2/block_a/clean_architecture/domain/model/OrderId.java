package pl.jakubtworek.backend_engineering.stage_2.block_a.clean_architecture.domain.model;

// Value object representing an order identifier.
// It is independent from persistence and transport formats.
public record OrderId(String value) {

    public OrderId {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("OrderId cannot be empty");
        }
    }
}