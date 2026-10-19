package pl.jakubtworek.cloudarchitecture.dto;

import java.math.BigDecimal;

/**
 * Data Transfer Object used at the API boundary.
 *
 * DTOs isolate public API contracts from internal persistence models.
 */
public record ProductDto(Long id, String name, BigDecimal price) {
    public ProductDto {
        if (id != null && id <= 0) {
            throw new IllegalArgumentException("id must be positive when provided");
        }
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("name must not be blank");
        }
        if (price == null || price.signum() < 0) {
            throw new IllegalArgumentException("price must not be negative");
        }
    }
}
