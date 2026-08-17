package com.ridesharing.mvp.integration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.Map;

/**
 * Klient HTTP do komunikacji z osobnym Pricing Service.
 *
 * W aplikacji ride-sharing Pricing Service odpowiada za wycenę przejazdu:
 * - cenę bazową,
 * - koszt dystansu,
 * - koszt czasu,
 * - surge pricing,
 * - walutę,
 * - ewentualne promocje lub opłaty dodatkowe.
 *
 * Ta klasa jest adapterem integracyjnym. Core API nie liczy ceny bezpośrednio,
 * tylko deleguje wycenę do dedykowanego serwisu.
 */
@Component
public class PricingServiceClient {

    /**
     * RestClient skonfigurowany bazowym adresem Pricing Service.
     *
     * Dzięki temu kod biznesowy nie musi znać pełnego URL-a endpointu,
     * tylko korzysta z metody estimate().
     */
    private final RestClient restClient;

    /**
     * Tworzy klienta Pricing Service.
     *
     * Konfiguracja:
     * app.services.pricing-service-url
     *
     * Domyślny adres lokalny to http://localhost:8083.
     * W środowisku kontenerowym powinien wskazywać np. na http://pricing-service:8083.
     */
    public PricingServiceClient(
            @Value("${app.services.pricing-service-url:http://localhost:8083}") String baseUrl
    ) {
        this.restClient = RestClient.builder()
                .baseUrl(baseUrl)
                .build();
    }

    /**
     * Wysyła request o wycenę przejazdu do Pricing Service.
     *
     * Endpoint:
     * POST /api/v1/pricing/estimate
     *
     * Typowy request może zawierać:
     * - cityId,
     * - pickupLat / pickupLng,
     * - dropoffLat / dropoffLng,
     * - distanceKm,
     * - durationMinutes,
     * - vehicleType,
     * - passengerId,
     * - currentDemand,
     * - availableDrivers.
     *
     * Typowa odpowiedź może zawierać:
     * - estimatedPrice,
     * - currency,
     * - surgeMultiplier,
     * - priceBreakdown,
     * - expiresAt.
     *
     * W MVP używana jest mapa, żeby szybko integrować serwisy.
     * Produkcyjnie lepiej użyć jawnych DTO, np. PricingEstimateRequest i PricingEstimateResponse.
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> estimate(Map<String, Object> request) {
        return restClient.post()
                .uri("/api/v1/pricing/estimate")
                .body(request)
                .retrieve()
                .body(Map.class);
    }
}