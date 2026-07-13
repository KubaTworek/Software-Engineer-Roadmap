package com.ridesharing.mvp.integration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.Map;

/**
 * Klient HTTP do komunikacji z zewnętrznym Fraud Service.
 *
 * W architekturze ride-sharing Fraud Service ocenia ryzyko operacji, np.:
 * - zamówienia przejazdu,
 * - płatności,
 * - podejrzanej lokalizacji,
 * - nadużywania promocji,
 * - nietypowych anulowań.
 *
 * Ta klasa nie zawiera logiki fraudowej. Jest tylko adapterem integracyjnym,
 * który wysyła dane do osobnego serwisu i zwraca jego odpowiedź.
 */
@Component
public class FraudServiceClient {

    /**
     * Spring RestClient skonfigurowany z bazowym URL-em Fraud Service.
     *
     * Dzięki temu pozostały kod aplikacji nie musi znać pełnego adresu endpointu,
     * tylko wywołuje metodę assess().
     */
    private final RestClient restClient;

    /**
     * Tworzy klienta Fraud Service.
     *
     * URL jest pobierany z konfiguracji:
     * app.services.fraud-service-url
     *
     * Jeżeli konfiguracja nie istnieje, używany jest domyślny adres lokalny:
     * http://localhost:8084
     *
     * To ułatwia uruchomienie projektu developersko bez pełnej konfiguracji środowiska.
     */
    public FraudServiceClient(
            @Value("${app.services.fraud-service-url:http://localhost:8084}") String baseUrl
    ) {
        this.restClient = RestClient.builder()
                .baseUrl(baseUrl)
                .build();
    }

    /**
     * Wysyła request do Fraud Service i zwraca wynik oceny ryzyka.
     *
     * Endpoint:
     * POST /api/v1/fraud/assess
     *
     * Przykładowy request może zawierać:
     * - passengerId,
     * - driverId,
     * - rideId,
     * - cityId,
     * - pickup/dropoff,
     * - payment method,
     * - liczbę anulowań,
     * - informacje o urządzeniu.
     *
     * Przykładowa odpowiedź może zawierać:
     * - riskScore,
     * - decision: ALLOW / REVIEW / BLOCK,
     * - reasons.
     *
     * W obecnej wersji używamy Map<String, Object>, co jest szybkie dla MVP,
     * ale słabsze typowo. Produkcyjnie lepiej zastąpić to konkretnymi DTO.
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> assess(Map<String, Object> request) {
        return restClient.post()
                .uri("/api/v1/fraud/assess")
                .body(request)
                .retrieve()
                .body(Map.class);
    }
}