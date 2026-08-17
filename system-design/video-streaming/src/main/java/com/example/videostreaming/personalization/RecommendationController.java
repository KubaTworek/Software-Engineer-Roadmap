package com.example.videostreaming.personalization;

import com.example.videostreaming.auth.User;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import static com.example.videostreaming.personalization.PersonalizationDtos.*;

/**
 * Kontroler rekomendacji wideo.
 *
 * Główna odpowiedzialność:
 * - wystawia endpoint dla rekomendowanych filmów,
 * - identyfikuje użytkownika na podstawie AuthenticationPrincipal,
 * - przekazuje userId i limit do RecommendationService,
 * - zwraca gotową listę rekomendacji.
 *
 * Ten kontroler nie liczy rekomendacji samodzielnie.
 * Logika wyboru kandydatów, scoringu, filtrowania obejrzanych filmów
 * i budowania odpowiedzi znajduje się w RecommendationService.
 */
@RestController
@RequestMapping("/api/recommendations")
public class RecommendationController {

    /**
     * Serwis rekomendacji.
     *
     * Odpowiada za właściwą personalizację:
     * - pobranie kandydatów z recommendation_candidates,
     * - wykorzystanie feature store,
     * - sortowanie po score,
     * - fallback do popularnych/nowych filmów,
     * - zbudowanie DTO odpowiedzi.
     */
    private final RecommendationService recommendations;

    public RecommendationController(RecommendationService recommendations) {
        this.recommendations = recommendations;
    }

    /**
     * Zwraca rekomendowane filmy dla aktualnie zalogowanego użytkownika.
     *
     * Endpoint używany np. przez:
     * - sekcję "Polecane dla Ciebie",
     * - homepage,
     * - ekran po zakończeniu oglądania,
     * - moduł rekomendacji w katalogu.
     *
     * Flow:
     * 1. Klient wywołuje endpoint.
     * 2. Backend bierze userId z AuthenticationPrincipal.
     * 3. RecommendationService pobiera i sortuje rekomendacje dla tego użytkownika.
     * 4. API zwraca listę filmów z powodami i score.
     *
     * userId nie pochodzi z requestu.
     * Dzięki temu użytkownik nie może pobrać rekomendacji innego konta.
     *
     * limit określa maksymalną liczbę wyników.
     * Dobrze, żeby RecommendationService dodatkowo ograniczał go
     * do bezpiecznego maksimum, np. 50 lub 100.
     */
    @GetMapping("/videos")
    public RecommendationResponse videos(@AuthenticationPrincipal User user,
                                         @RequestParam(defaultValue = "20") int limit) {
        return recommendations.recommendations(user.getId(), limit);
    }
}