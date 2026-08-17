package com.example.videostreaming.personalization;

import com.example.videostreaming.auth.User;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import static com.example.videostreaming.personalization.PersonalizationDtos.*;

/**
 * Kontroler rankingu treści.
 *
 * Główna odpowiedzialność:
 * - wystawia endpoint dla rankingu strony głównej,
 * - przekazuje userId do RecommendationService,
 * - zwraca gotową, uporządkowaną listę treści dla użytkownika.
 *
 * Ten kontroler nie liczy rankingu samodzielnie.
 * Ranking, rekomendacje, kandydaci i scoring są obsługiwane w RecommendationService.
 */
@RestController
@RequestMapping("/api/ranking")
public class RankingController {

    /**
     * Serwis rekomendacji i rankingu.
     *
     * Odpowiada za:
     * - pobranie kandydatów rekomendacji,
     * - uwzględnienie cech użytkownika i filmu,
     * - sortowanie po score,
     * - zastosowanie limitu wyników,
     * - zbudowanie DTO odpowiedzi.
     */
    private final RecommendationService recommendations;

    public RankingController(RecommendationService recommendations) {
        this.recommendations = recommendations;
    }

    /**
     * Zwraca ranking treści dla strony głównej.
     *
     * Endpoint używany przez homepage aplikacji.
     *
     * Flow:
     * 1. Frontend otwiera stronę główną.
     * 2. Backend bierze userId z AuthenticationPrincipal.
     * 3. RecommendationService liczy lub pobiera ranking dla tego użytkownika.
     * 4. API zwraca listę filmów uporządkowaną według score.
     *
     * userId nie jest przyjmowane z requestu.
     * Dzięki temu użytkownik nie może pobrać rankingu innego konta.
     *
     * limit ogranicza liczbę zwróconych pozycji.
     * Warto, żeby RecommendationService dodatkowo przycinał limit
     * do bezpiecznego maksimum, np. 50 lub 100.
     */
    @GetMapping("/home")
    public RankingResponse home(@AuthenticationPrincipal User user,
                                @RequestParam(defaultValue = "20") int limit) {
        return recommendations.homeRanking(user.getId(), limit);
    }
}