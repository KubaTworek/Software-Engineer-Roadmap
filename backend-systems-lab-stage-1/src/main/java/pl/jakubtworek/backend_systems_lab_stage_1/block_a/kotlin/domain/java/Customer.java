package pl.jakubtworek.backend_systems_lab_stage_1.block_a.kotlin.domain.java;

// Customer is also modeled as a record.
// Java records automatically generate constructor,
// accessors, equals(), hashCode(), and toString().
public record Customer(
        String id,
        String fullName
) {

    public Customer {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("Customer id cannot be empty");
        }

        if (fullName == null || fullName.isBlank()) {
            throw new IllegalArgumentException("Customer name cannot be empty");
        }
    }
}