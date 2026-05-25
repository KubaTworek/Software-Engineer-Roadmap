package pl.jakubtworek.backend_engineering.stage_3.block_a.implementation.src.main.java.pl.jakubtworek.systemdesign.implementation.degradation;

import java.util.List;

/**
 * Example product response.
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