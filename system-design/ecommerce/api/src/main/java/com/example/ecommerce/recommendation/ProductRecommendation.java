package com.example.ecommerce.recommendation;

import jakarta.persistence.*;

@Entity
@Table(name = "product_recommendations", indexes = {
        @Index(name = "idx_recommendations_product", columnList = "productId")
})
public class ProductRecommendation {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long productId;
    private Long recommendedProductId;

    @Column(nullable = false)
    private double score;

    @Column(nullable = false)
    private String reason;

    protected ProductRecommendation() {}

    public ProductRecommendation(Long productId, Long recommendedProductId, double score, String reason) {
        this.productId = productId;
        this.recommendedProductId = recommendedProductId;
        this.score = score;
        this.reason = reason;
    }

    public Long getId() { return id; }
    public Long getProductId() { return productId; }
    public Long getRecommendedProductId() { return recommendedProductId; }
    public double getScore() { return score; }
    public String getReason() { return reason; }
}
