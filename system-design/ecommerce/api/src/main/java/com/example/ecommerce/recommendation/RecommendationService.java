package com.example.ecommerce.recommendation;

import com.example.ecommerce.catalog.ProductRepository;
import com.example.ecommerce.recommendation.dto.RecommendationDtos;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Serwis domenowy odpowiedzialny za rekomendacje produktowe.
 *
 * Rekomendacje są używane np. na stronie produktu do pokazania:
 * - podobnych produktów,
 * - produktów komplementarnych,
 * - cross-sellu,
 * - upsellu,
 * - sekcji "Klienci kupili również".
 *
 * W tej wersji serwis działa prosto:
 * 1. Najpierw sprawdza ręcznie zapisane rekomendacje.
 * 2. Jeśli ich nie ma, zwraca fallback z listy innych produktów.
 *
 * To nie jest jeszcze ML ani personalizacja.
 * To praktyczne MVP, które pozwala mieć działający endpoint rekomendacji.
 */
@Service
public class RecommendationService {

    /**
     * Repozytorium zapisanych rekomendacji.
     *
     * Przechowuje relacje:
     * productId -> recommendedProductId
     *
     * Każda rekomendacja ma score i reason.
     * Score pozwala sortować rekomendacje od najlepszych.
     */
    private final ProductRecommendationRepository recommendations;

    /**
     * Repozytorium produktów.
     *
     * Używane jako fallback, gdy nie ma zapisanych rekomendacji
     * dla konkretnego produktu.
     */
    private final ProductRepository products;

    /**
     * Constructor injection.
     *
     * Serwis potrzebuje repozytorium rekomendacji oraz produktów.
     */
    public RecommendationService(
            ProductRecommendationRepository recommendations,
            ProductRepository products
    ) {
        this.recommendations = recommendations;
        this.products = products;
    }

    /**
     * Zwraca rekomendacje dla danego produktu.
     *
     * Flow:
     * 1. Pobierz maksymalnie 10 zapisanych rekomendacji dla productId.
     * 2. Posortuj je malejąco po score.
     * 3. Jeśli istnieją, zwróć je jako DTO.
     * 4. Jeśli nie istnieją, użyj fallbacku.
     *
     * @Transactional(readOnly = true):
     * metoda tylko czyta dane, więc może działać na read-replice.
     */
    @Transactional(readOnly = true)
    public List<RecommendationDtos.RecommendationResponse> recommendations(Long productId) {
        /*
         * Najpierw sprawdzamy rekomendacje zapisane w tabeli product_recommendations.
         *
         * To mogą być:
         * - ręczne rekomendacje merchandisera,
         * - rekomendacje wygenerowane batchowo,
         * - wynik prostego algorytmu offline,
         * - dane zaimportowane z zewnętrznego systemu rekomendacyjnego.
         */
        var stored = recommendations.findTop10ByProductIdOrderByScoreDesc(productId);

        /*
         * Jeśli są zapisane rekomendacje, zwracamy je.
         *
         * Kolejność ma znaczenie, bo repozytorium sortuje po score DESC,
         * czyli najlepsze rekomendacje idą pierwsze.
         */
        if (!stored.isEmpty()) {
            return stored.stream()
                    .map(this::toResponse)
                    .toList();
        }

        /*
         * Fallback, gdy nie ma żadnych rekomendacji dla produktu.
         *
         * Zwracamy pierwsze 5 innych produktów.
         *
         * To bardzo proste zachowanie MVP:
         * - lepsze niż pusta sekcja rekomendacji,
         * - pozwala frontendowi zawsze pokazać jakieś produkty,
         * - nie wymaga jeszcze eventów behawioralnych ani ML.
         *
         * Uwaga produkcyjna:
         * products.findAll() nie skaluje się dla dużego katalogu.
         * Lepiej byłoby pobrać popularne produkty, produkty z tej samej kategorii
         * albo rekomendacje z dedykowanego indeksu.
         */
        return products.findAll()
                .stream()
                .filter(product -> !product.getId().equals(productId))
                .limit(5)
                .map(product -> new RecommendationDtos.RecommendationResponse(
                        productId,
                        product.getId(),
                        0.1,
                        "POPULAR_FALLBACK"
                ))
                .toList();
    }

    /**
     * Dodaje ręczną rekomendację produktu.
     *
     * Metoda przydatna dla:
     * - seedowania danych,
     * - panelu admina,
     * - merchandisera,
     * - prostego cross-sellu.
     *
     * Parametry:
     * - productId — produkt bazowy,
     * - recommendedProductId — produkt polecany,
     * - score — siła rekomendacji,
     * - reason — powód rekomendacji, np. MANUAL, SAME_CATEGORY, POPULAR.
     */
    @Transactional
    public void addManualRecommendation(
            Long productId,
            Long recommendedProductId,
            double score,
            String reason
    ) {
        recommendations.save(
                new ProductRecommendation(
                        productId,
                        recommendedProductId,
                        score,
                        reason
                )
        );
    }

    /**
     * Mapuje encję ProductRecommendation na DTO odpowiedzi API.
     *
     * DTO zawiera:
     * - productId,
     * - recommendedProductId,
     * - score,
     * - reason.
     *
     * Nie zwracamy encji JPA bezpośrednio na zewnątrz.
     */
    private RecommendationDtos.RecommendationResponse toResponse(
            ProductRecommendation recommendation
    ) {
        return new RecommendationDtos.RecommendationResponse(
                recommendation.getProductId(),
                recommendation.getRecommendedProductId(),
                recommendation.getScore(),
                recommendation.getReason()
        );
    }
}