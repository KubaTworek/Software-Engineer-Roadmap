package pl.jakubtworek.backend_engineering.stage_3.block_a.concepts.src.main.java.pl.jakubtworek.systemdesign.concepts.degradation;

import java.util.List;

/**
 * Optional recommendation dependency.
 */
public interface RecommendationClient {

    List<String> recommendationsFor(String productId);
}