package pl.jakubtworek.backend_engineering.stage_3.block_a.implementation.src.main.java.pl.jakubtworek.systemdesign.implementation.degradation;

import java.util.List;

/**
 * Optional dependency used by ProductPageService.
 */
public interface RecommendationClient {

    List<String> recommend(String productId);
}