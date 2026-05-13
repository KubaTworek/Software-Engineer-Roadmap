package pl.jakubtworek.backend_systems_lab_stage_1.block_a.kotlin.domain.java;

import java.math.BigDecimal;

// Order represents a domain object in Java.
// A Java record reduces boilerplate compared to a classic class,
// but validation and domain behavior still need to be written explicitly.
public record Order(
        String id,
        Customer customer,
        BigDecimal totalAmount,
        OrderStatus status
) {

    public Order {
        // Compact constructor validation.
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("Order id cannot be empty");
        }

        if (customer == null) {
            throw new IllegalArgumentException("Customer cannot be null");
        }

        if (totalAmount == null || totalAmount.signum() < 0) {
            throw new IllegalArgumentException("Total amount cannot be negative");
        }

        if (status == null) {
            throw new IllegalArgumentException("Status cannot be null");
        }
    }

    // Domain behavior belongs close to the data it operates on.
    public boolean isCompleted() {
        return status == OrderStatus.COMPLETED;
    }
}