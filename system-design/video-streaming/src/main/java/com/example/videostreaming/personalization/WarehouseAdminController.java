package com.example.videostreaming.personalization;

import org.springframework.web.bind.annotation.*;

import static com.example.videostreaming.personalization.PersonalizationDtos.*;

/**
 * Administracyjny kontroler lokalnego data warehouse.
 *
 * Główna odpowiedzialność:
 * - pozwala ręcznie odświeżyć agregaty personalizacji,
 * - pozwala podejrzeć dzienne metryki filmów,
 * - udostępnia dane diagnostyczne dla admina/analityki.
 *
 * W tym projekcie data warehouse jest uproszczony i trzymany w PostgreSQL.
 * Produkcyjnie taka warstwa zwykle byłaby poza główną bazą aplikacyjną,
 * np. w BigQuery, Snowflake, Redshift albo ClickHouse.
 *
 * Ważne:
 * To są endpointy administracyjne.
 * W produkcji powinny być zabezpieczone rolą ADMIN,
 * np. przez @PreAuthorize("hasRole('ADMIN')").
 */
@RestController
@RequestMapping("/api/admin/warehouse")
public class WarehouseAdminController {

    /**
     * Serwis feature store i lokalnych agregacji.
     *
     * Używany tutaj do:
     * - pełnego przeliczenia warehouse + feature store,
     * - pobrania dziennych metryk video.
     */
    private final FeatureStoreService features;

    public WarehouseAdminController(FeatureStoreService features) {
        this.features = features;
    }

    /**
     * Ręcznie odświeża lokalny warehouse i feature store.
     *
     * Flow:
     * 1. Admin wywołuje endpoint.
     * 2. FeatureStoreService przelicza dzienne metryki video.
     * 3. Przeliczane są cechy użytkowników.
     * 4. Przeliczane są cechy filmów.
     * 5. Generowani są kandydaci rekomendacji.
     * 6. API zwraca podsumowanie przeliczenia.
     *
     * Endpoint przydatny:
     * - podczas developmentu,
     * - po imporcie eventów,
     * - po zmianie scoringu,
     * - gdy chcemy wymusić świeże dane przed testami.
     *
     * Uwaga:
     * Przy większej ilości danych ta operacja może być ciężka.
     * Produkcyjnie lepiej wykonywać ją jako background job,
     * a nie synchronicznie w request-response.
     */
    @PostMapping("/refresh")
    public FeatureRecomputeResponse refresh() {
        return features.recompute();
    }

    /**
     * Zwraca dzienne metryki video z lokalnego warehouse.
     *
     * Endpoint diagnostyczny dla admina/analityki.
     *
     * Parametry:
     * - days: liczba dni do pobrania, ograniczona do zakresu 1–90,
     * - limit: maksymalna liczba rekordów, ograniczona do zakresu 1–500.
     *
     * Zwracane dane mogą obejmować:
     * - views,
     * - starts,
     * - completions,
     * - unique users,
     * - avg startup time,
     * - rebuffer ratio.
     *
     * Te metryki są później używane m.in. do:
     * - feature_store_video,
     * - trending score,
     * - quality score,
     * - rekomendacji i rankingu.
     */
    @GetMapping("/daily-video-metrics")
    public DailyVideoMetricsResponse dailyVideoMetrics(@RequestParam(defaultValue = "7") int days,
                                                       @RequestParam(defaultValue = "100") int limit) {
        /*
         * Normalizujemy parametry wejściowe.
         *
         * days:
         * - minimum 1 dzień,
         * - maksimum 90 dni.
         *
         * limit:
         * - minimum 1 rekord,
         * - maksimum 500 rekordów.
         *
         * To chroni endpoint przed przypadkowo ciężkimi zapytaniami.
         */
        int safeDays = Math.min(Math.max(days, 1), 90);
        int safeLimit = Math.min(Math.max(limit, 1), 500);

        return new DailyVideoMetricsResponse(
                features.dailyMetrics(safeDays, safeLimit)
        );
    }
}