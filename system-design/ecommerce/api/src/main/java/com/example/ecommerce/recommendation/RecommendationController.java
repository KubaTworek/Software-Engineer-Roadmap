package com.example.ecommerce.recommendation;

import com.example.ecommerce.recommendation.dto.RecommendationDtos;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * REST controller odpowiedzialny za publiczne API rekomendacji produktowych.
 *
 * Rekomendacje w e-commerce pomagają pokazać klientowi produkty powiązane
 * z aktualnie oglądanym produktem.
 *
 * Typowe zastosowania:
 * - "Podobne produkty",
 * - "Klienci kupili również",
 * - cross-sell,
 * - upsell,
 * - rekomendacje na stronie produktu.
 *
 * Controller nie zawiera logiki rekomendacyjnej.
 * Deleguje całość do RecommendationService.
 */
@RestController
@RequestMapping("/api/recommendations")
public class RecommendationController {

    /**
     * Serwis rekomendacji.
     *
     * Odpowiada za właściwą logikę:
     * - pobranie rekomendacji dla produktu,
     * - użycie ręcznie zapisanych rekomendacji,
     * - fallback do popularnych produktów,
     * - mapowanie wyniku na DTO.
     */
    private final RecommendationService recommendations;

    /**
     * Constructor injection.
     *
     * Controller potrzebuje tylko RecommendationService.
     * Nie powinien samodzielnie pobierać produktów ani liczyć rekomendacji.
     */
    public RecommendationController(RecommendationService recommendations) {
        this.recommendations = recommendations;
    }

    /**
     * Zwraca rekomendacje dla konkretnego produktu.
     *
     * Endpoint:
     * GET /api/recommendations/products/{productId}
     *
     * productId oznacza produkt bazowy, dla którego chcemy znaleźć produkty polecane.
     *
     * Przykład:
     * klient ogląda produkt ID=10,
     * frontend pyta o rekomendacje dla productId=10,
     * API zwraca listę innych produktów do pokazania w sekcji rekomendacji.
     *
     * W tej wersji controller nie sprawdza, czy produkt istnieje.
     * Tę decyzję zostawia RecommendationService.
     */
    @GetMapping("/products/{productId}")
    public List<RecommendationDtos.RecommendationResponse> recommendations(
            @PathVariable Long productId
    ) {
        return recommendations.recommendations(productId);
    }
}