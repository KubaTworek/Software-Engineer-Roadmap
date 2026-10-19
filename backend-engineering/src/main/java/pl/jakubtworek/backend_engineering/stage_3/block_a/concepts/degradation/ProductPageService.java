package pl.jakubtworek.backend_engineering.stage_3.block_a.concepts.degradation;

import java.util.List;

/**
 * Demonstrates graceful degradation.
 *
 * The product page still works when recommendations are disabled
 * or when the recommendation dependency fails.
 */
public class ProductPageService {

    private final EmergencyLeverRegistry emergencyLevers;
    private final RecommendationClient recommendationClient;

    public ProductPageService(
            EmergencyLeverRegistry emergencyLevers,
            RecommendationClient recommendationClient
    ) {
        if (emergencyLevers == null) throw new IllegalArgumentException("emergencyLevers is required");
        if (recommendationClient == null) throw new IllegalArgumentException("recommendationClient is required");
        this.emergencyLevers = emergencyLevers;
        this.recommendationClient = recommendationClient;
    }

    public ProductPage getProductPage(String productId) {
        if (productId == null || productId.isBlank()) throw new IllegalArgumentException("productId is required");
        String name = "Product " + productId;
        String description = "Basic product description";

        if (emergencyLevers.isEnabled(EmergencyLever.DISABLE_RECOMMENDATIONS)) {
            return new ProductPage(productId, name, description, List.of(), true);
        }

        try {
            return new ProductPage(
                    productId,
                    name,
                    description,
                    recommendationClient.recommendationsFor(productId),
                    false
            );
        } catch (Exception ignored) {
            return new ProductPage(productId, name, description, List.of(), true);
        }
    }
}
