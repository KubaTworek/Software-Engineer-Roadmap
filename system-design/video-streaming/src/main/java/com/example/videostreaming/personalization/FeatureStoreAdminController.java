package com.example.videostreaming.personalization;

import org.springframework.web.bind.annotation.*;

import java.util.UUID;

import static com.example.videostreaming.personalization.PersonalizationDtos.*;

/**
 * Administracyjny kontroler feature store.
 *
 * Główna odpowiedzialność:
 * - pozwala ręcznie przeliczyć feature store,
 * - pozwala podejrzeć cechy konkretnego użytkownika,
 * - pozwala podejrzeć cechy konkretnego filmu.
 *
 * Feature store przechowuje dane używane przez personalizację,
 * rekomendacje, ranking i eksperymenty.
 *
 * Przykłady cech:
 * - liczba obejrzanych filmów użytkownika,
 * - ulubione kategorie użytkownika,
 * - popularność filmu,
 * - completion rate filmu,
 * - liczba odtworzeń w ostatnim okresie.
 *
 * Ważne:
 * To jest endpoint administracyjny.
 * W produkcji powinien być zabezpieczony rolą ADMIN,
 * np. przez @PreAuthorize("hasRole('ADMIN')").
 */
@RestController
@RequestMapping("/api/admin/features")
public class FeatureStoreAdminController {

    /**
     * Serwis feature store.
     *
     * Zawiera właściwą logikę:
     * - przeliczania cech,
     * - pobierania cech użytkownika,
     * - pobierania cech filmu.
     *
     * Kontroler tylko wystawia tę logikę przez HTTP.
     */
    private final FeatureStoreService features;

    public FeatureStoreAdminController(FeatureStoreService features) {
        this.features = features;
    }

    /**
     * Ręcznie uruchamia przeliczenie feature store.
     *
     * Endpoint przydatny:
     * - po imporcie danych,
     * - po zmianie logiki cech,
     * - podczas developmentu,
     * - przed testowaniem rekomendacji/rankingu,
     * - gdy scheduler przeliczeń nie jest uruchomiony.
     *
     * Flow:
     * 1. Admin wywołuje endpoint.
     * 2. FeatureStoreService agreguje dane źródłowe.
     * 3. System aktualizuje feature_store_user i feature_store_video.
     * 4. API zwraca podsumowanie przeliczenia.
     *
     * Uwaga:
     * Jeśli przeliczenie jest ciężkie, produkcyjnie lepiej zlecić je do kolejki
     * i zwrócić jobId zamiast wykonywać całość synchronicznie.
     */
    @PostMapping("/recompute")
    public FeatureRecomputeResponse recompute() {
        return features.recompute();
    }

    /**
     * Zwraca cechy konkretnego użytkownika.
     *
     * Endpoint diagnostyczny dla admina.
     *
     * Używany do sprawdzenia, dlaczego system rekomenduje użytkownikowi
     * określone filmy albo jak użytkownik wygląda z perspektywy personalizacji.
     *
     * @param userId użytkownik, którego cechy chcemy podejrzeć
     * @return aktualne cechy użytkownika z feature store
     */
    @GetMapping("/users/{userId}")
    public UserFeatureResponse userFeature(@PathVariable UUID userId) {
        return features.userFeature(userId);
    }

    /**
     * Zwraca cechy konkretnego filmu.
     *
     * Endpoint diagnostyczny dla admina.
     *
     * Używany do sprawdzenia, jak film jest widziany przez ranking
     * i rekomendacje, np. popularność, watch count, completion rate,
     * trending score albo inne agregaty.
     *
     * @param videoId film, którego cechy chcemy podejrzeć
     * @return aktualne cechy filmu z feature store
     */
    @GetMapping("/videos/{videoId}")
    public VideoFeatureResponse videoFeature(@PathVariable UUID videoId) {
        return features.videoFeature(videoId);
    }
}