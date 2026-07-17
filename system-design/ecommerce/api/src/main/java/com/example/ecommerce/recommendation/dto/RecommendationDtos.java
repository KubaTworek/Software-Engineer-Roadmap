package com.example.ecommerce.recommendation.dto;

public final class RecommendationDtos {
    private RecommendationDtos() {}

    public record RecommendationResponse(
            Long productId,
            Long recommendedProductId,
            double score,
            String reason
    ) {}
}
