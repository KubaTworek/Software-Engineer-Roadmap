package pl.jakubtworek.backend_engineering.stage_3.block_a.concepts.degradation;

import java.util.List;

/**
 * Product page response.
 *
 * Recommendations are optional and can be removed during degradation.
 */
public record ProductPage(
        String productId,
        String name,
        String description,
        List<String> recommendations,
        boolean degraded
) {
    public ProductPage {
        if (productId == null || productId.isBlank()) throw new IllegalArgumentException("productId is required");
        if (name == null || name.isBlank()) throw new IllegalArgumentException("name is required");
        if (description == null) throw new IllegalArgumentException("description is required");
        recommendations = List.copyOf(recommendations);
    }
}
