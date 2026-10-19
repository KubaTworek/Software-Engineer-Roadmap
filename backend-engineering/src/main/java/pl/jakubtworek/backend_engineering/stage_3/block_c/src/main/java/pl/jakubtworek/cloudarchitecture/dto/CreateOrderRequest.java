package pl.jakubtworek.cloudarchitecture.dto;

import java.util.List;
import java.util.Objects;

/**
 * Request body for order creation.
 *
 * This object should contain only client-provided input and no server-side state.
 */
public record CreateOrderRequest(String customerId, List<Long> productIds) {
    public CreateOrderRequest {
        if (customerId == null || customerId.isBlank()) {
            throw new IllegalArgumentException("customerId must not be blank");
        }
        productIds = List.copyOf(Objects.requireNonNull(productIds, "productIds must not be null"));
        if (productIds.isEmpty()) {
            throw new IllegalArgumentException("productIds must not be empty");
        }
        if (productIds.stream().anyMatch(id -> id == null || id <= 0)) {
            throw new IllegalArgumentException("productIds must contain only positive identifiers");
        }
    }
}
