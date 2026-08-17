package com.example.ecommerce.recommendation;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ProductRecommendationRepository extends JpaRepository<ProductRecommendation, Long> {
    List<ProductRecommendation> findTop10ByProductIdOrderByScoreDesc(Long productId);
}
