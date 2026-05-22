package pl.jakubtworek.backend_engineering.stage_2.block_a.clean_architecture.domain.model;

// Value object representing a customer identifier.
// The domain depends on explicit concepts, not primitive strings everywhere.
public record CustomerId(String value) {

    public CustomerId {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("CustomerId cannot be empty");
        }
    }
}