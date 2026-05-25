package pl.jakubtworek.backend_engineering.stage_3.block_a.implementation.src.main.java.pl.jakubtworek.systemdesign.implementation.degradation;

import java.util.List;

/**
 * Demonstrates graceful degradation.
 *
 * If recommendations are disabled or unavailable, the core product page
 * is still returned without optional recommendations.
 */
public class ProductPageService {

    private final EmergencyLeverRegistry emergencyLeverRegistry;
    private final RecommendationClient recommendationClient;

    public ProductPageService(
            EmergencyLeverRegistry emergencyLeverRegistry,
            RecommendationClient recommendationClient
    ) {
        this.emergencyLeverRegistry = emergencyLeverRegistry;
        this.recommendationClient = recommendationClient;
    }

    public ProductPage getProductPage(String productId) {
        String name = "Product " + productId;
        String description = "Basic product description";

        if (emergencyLeverRegistry.isEnabled(EmergencyLever.DISABLE_RECOMMENDATIONS)) {
            return new ProductPage(productId, name, description, List.of(), true);
        }

        try {
            List<String> recommendations = recommendationClient.recommend(productId);
            return new ProductPage(productId, name, description, recommendations, false);
        } catch (Exception ignored) {
            return new ProductPage(productId, name, description, List.of(), true);
        }
    }
}