package com.ridesharing.mvp.integration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;

/**
 * Klient HTTP do komunikacji z osobnym Location Service.
 *
 * W Etapie 3 Location Service został wydzielony z core-api, ponieważ lokalizacja
 * kierowców jest jednym z najbardziej obciążonych obszarów systemu.
 *
 * Ta klasa jest adapterem integracyjnym:
 * - core-api nie musi znać szczegółów implementacji Location Service,
 * - matching może pobierać kandydatów przez prostą metodę nearbyDrivers(),
 * - URL usługi jest konfigurowalny per środowisko.
 */
@Component
public class LocationServiceClient {

    /**
     * RestClient skonfigurowany bazowym adresem Location Service.
     *
     * Wszystkie wywołania z tej klasy będą wykonywane względem baseUrl,
     * np. http://localhost:8081 w środowisku lokalnym.
     */
    private final RestClient restClient;

    /**
     * Tworzy klienta Location Service.
     *
     * Konfiguracja:
     * app.services.location-service-url
     *
     * Domyślnie wskazuje na lokalny Location Service na porcie 8081.
     * W Docker Compose albo Kubernetes ten adres powinien wskazywać na nazwę usługi,
     * np. http://location-service:8081.
     */
    public LocationServiceClient(
            @Value("${app.services.location-service-url:http://localhost:8081}") String baseUrl
    ) {
        this.restClient = RestClient.builder()
                .baseUrl(baseUrl)
                .build();
    }

    /**
     * Pobiera listę kierowców dostępnych w pobliżu wskazanego punktu.
     *
     * Endpoint Location Service:
     * GET /api/v1/locations/nearby-drivers
     *
     * Parametry:
     * - cityId: miasto/region operacyjny, ważne dla shardingu i ograniczenia wyszukiwania,
     * - lat/lng: punkt startowy pasażera,
     * - ringSize: zakres wyszukiwania po pierścieniach H3/S2,
     * - limit: maksymalna liczba kandydatów zwróconych do matchingu.
     *
     * Wynik jest używany przez Matching Service/Core API jako początkowa pula kandydatów.
     * To nie musi być finalny kierowca — później można jeszcze zastosować ranking,
     * ETA, rating, acceptance rate, typ pojazdu i fraud score.
     */
    @SuppressWarnings("unchecked")
    public List<Map<String, Object>> nearbyDrivers(
            String cityId,
            double lat,
            double lng,
            int ringSize,
            int limit
    ) {
        return restClient.get()
                .uri(uri -> uri.path("/api/v1/locations/nearby-drivers")
                        .queryParam("cityId", cityId)
                        .queryParam("lat", lat)
                        .queryParam("lng", lng)
                        .queryParam("ringSize", ringSize)
                        .queryParam("limit", limit)
                        .build())
                .retrieve()
                .body(List.class);
    }
}