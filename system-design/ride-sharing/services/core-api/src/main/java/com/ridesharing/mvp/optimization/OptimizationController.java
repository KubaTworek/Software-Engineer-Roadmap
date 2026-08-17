package com.ridesharing.mvp.optimization;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestClient;

import java.util.Map;

/**
 * Kontroler integracyjny dla Etapu 4 — Optymalizacja.
 *
 * Ten endpoint działa jako cienka warstwa proxy z core-api do usług optymalizacyjnych:
 * - ML ETA Service,
 * - ML Matching Service,
 * - Dynamic Pricing Service,
 * - Region Control Plane.
 *
 * W kontekście aplikacji ride-sharing nie jest to miejsce na właściwą logikę ML.
 * Ta klasa tylko przekazuje requesty do wyspecjalizowanych serwisów.
 */
@RestController
@RequestMapping("/api/v1/optimization")
public class OptimizationController {

    /**
     * Klient HTTP używany do komunikacji z usługami optymalizacyjnymi.
     *
     * Tutaj używany jest jeden generyczny RestClient.
     * Działa dla MVP, ale produkcyjnie lepiej mieć osobne, typowane klienty:
     * EtaServiceClient, MlMatchingClient, DynamicPricingClient, RegionClient.
     */
    private final RestClient restClient;

    /**
     * Bazowy URL ML ETA Service.
     *
     * Ten serwis przewiduje czas dojazdu lub czas przejazdu lepiej niż prosty routing,
     * bo docelowo może uwzględniać historyczny ruch, porę dnia, miasto i cechy trasy.
     */
    private final String etaUrl;

    /**
     * Bazowy URL ML Matching Service.
     *
     * Ten serwis rankinguje kandydatów na kierowców.
     * Docelowo może brać pod uwagę ETA, rating, acceptance probability,
     * cancel rate, typ pojazdu, fraud score i lokalny balans podaży.
     */
    private final String matchingUrl;

    /**
     * Bazowy URL Dynamic Pricing Service.
     *
     * Ten serwis liczy cenę dynamiczną, np. z uwzględnieniem surge,
     * popytu, dostępności kierowców, miasta i reguł bezpieczeństwa cenowego.
     */
    private final String pricingUrl;

    /**
     * Bazowy URL Region Control Plane.
     *
     * Ten komponent pomaga zdecydować, który region powinien obsłużyć dany aggregate,
     * np. rideId, driverId albo cityId.
     *
     * To element przygotowania pod multi-region active-active.
     */
    private final String regionUrl;

    /**
     * Konfiguruje adresy usług optymalizacyjnych.
     *
     * Każdy URL może pochodzić z:
     * - application.yml / application.properties,
     * - zmiennej środowiskowej,
     * - wartości domyślnej dla lokalnego developmentu.
     *
     * Przykład:
     * app.integrations.ml-eta-service-url
     * albo ML_ETA_SERVICE_URL.
     */
    public OptimizationController(
            RestClient.Builder builder,
            @Value("${app.integrations.ml-eta-service-url:${ML_ETA_SERVICE_URL:http://localhost:8086}}") String etaUrl,
            @Value("${app.integrations.ml-matching-service-url:${ML_MATCHING_SERVICE_URL:http://localhost:8087}}") String matchingUrl,
            @Value("${app.integrations.dynamic-pricing-service-url:${DYNAMIC_PRICING_SERVICE_URL:http://localhost:8090}}") String pricingUrl,
            @Value("${app.integrations.region-control-plane-url:${REGION_CONTROL_PLANE_URL:http://localhost:8091}}") String regionUrl
    ) {
        this.restClient = builder.build();
        this.etaUrl = etaUrl;
        this.matchingUrl = matchingUrl;
        this.pricingUrl = pricingUrl;
        this.regionUrl = regionUrl;
    }

    /**
     * Lekki endpoint kontrolny dla warstwy optymalizacji.
     *
     * Nie sprawdza realnie dostępności usług ML ani region control plane.
     * Zwraca tylko informację, że core-api ma włączoną warstwę Etapu 4.
     *
     * Produkcyjnie healthcheck powinien być rozdzielony:
     * - readiness dla core-api,
     * - health dependency checks dla ETA, matching, pricing i region service.
     */
    @GetMapping("/health")
    public Map<String, String> health() {
        return Map.of(
                "stage", "4",
                "mode", "ml-optimized-active-active-ready"
        );
    }

    /**
     * Pyta Region Control Plane, który region powinien obsłużyć dany aggregate.
     *
     * aggregateId to stabilny identyfikator domenowy, np. rideId albo driverId.
     * cityId może pomóc wymusić routing lokalny dla miasta.
     *
     * W multi-region active-active taka decyzja jest ważna, żeby:
     * - kierować requesty do najbliższego/właściwego regionu,
     * - ograniczyć konflikty zapisu,
     * - utrzymać locality danych przejazdu.
     */
    @GetMapping("/route-region")
    public String routeRegion(
            @RequestParam String aggregateId,
            @RequestParam(required = false) String cityId
    ) {
        return restClient.get()
                .uri(
                        regionUrl + "/api/v1/regions/route?aggregateId={id}&cityId={city}",
                        aggregateId,
                        cityId == null ? "" : cityId
                )
                .retrieve()
                .body(String.class);
    }

    /**
     * Przekazuje request do ML ETA Service.
     *
     * Endpoint:
     * POST /api/v1/ml/eta/predict
     *
     * Typowy payload może zawierać:
     * - origin,
     * - destination,
     * - cityId,
     * - timeOfDay,
     * - distanceKm,
     * - traffic features,
     * - driver state.
     *
     * Wynik powinien pomóc lepiej oszacować czas dojazdu kierowcy
     * i czas samego przejazdu.
     */
    @PostMapping("/eta")
    public String eta(@RequestBody String body) {
        return restClient.post()
                .uri(etaUrl + "/api/v1/ml/eta/predict")
                .body(body)
                .retrieve()
                .body(String.class);
    }

    /**
     * Przekazuje request do ML Matching Service.
     *
     * Endpoint:
     * POST /api/v1/ml/matching/rank
     *
     * Ten serwis nie powinien tworzyć przejazdu ani przypisywać kierowcy.
     * Jego odpowiedzialność to ranking kandydatów.
     *
     * Decyzję o finalnym przypisaniu nadal powinien wykonać core Matching/Ride Service,
     * ponieważ tam są blokady kierowcy, state machine i audyt.
     */
    @PostMapping("/matching-rank")
    public String matchingRank(@RequestBody String body) {
        return restClient.post()
                .uri(matchingUrl + "/api/v1/ml/matching/rank")
                .body(body)
                .retrieve()
                .body(String.class);
    }

    /**
     * Przekazuje request do Dynamic Pricing Service.
     *
     * Endpoint:
     * POST /api/v1/dynamic-pricing/estimate
     *
     * Ten serwis może uwzględniać:
     * - aktualny popyt,
     * - liczbę dostępnych kierowców,
     * - prognozę popytu,
     * - typ pojazdu,
     * - miasto,
     * - ograniczenia maksymalnego surge.
     *
     * Core API powinno traktować wynik jako estimate, a nie finalne rozliczenie,
     * dopóki przejazd nie zostanie zakończony.
     */
    @PostMapping("/dynamic-price")
    public String dynamicPrice(@RequestBody String body) {
        return restClient.post()
                .uri(pricingUrl + "/api/v1/dynamic-pricing/estimate")
                .body(body)
                .retrieve()
                .body(String.class);
    }
}