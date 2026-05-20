package pl.jakubtworek.backend_engineering.stage_2.block_a.use_case.domain.model;

// Strongly typed identifier for a customer.
// The Order aggregate references Customer by ID, not by object reference.
public record CustomerId(String value) {

    public CustomerId {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("CustomerId cannot be empty");
        }
    }

    public static CustomerId of(String value) {
        return new CustomerId(value);
    }
}