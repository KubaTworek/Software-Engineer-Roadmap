package pl.jakubtworek.backend_engineering.stage_3.block_a.concepts.src.main.java.pl.jakubtworek.systemdesign.concepts.degradation;

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
}