package com.example.newsfeed.region;

import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * Kontroler HTTP zwracający informację o regionie, w którym działa aplikacja.
 *
 * W aplikacji multi-region ważne jest, żeby wiedzieć,
 * z którego regionu odpowiada dana instancja systemu.
 *
 * Przykład:
 * - eu-central-1,
 * - us-east-1,
 * - local.
 *
 * Ten endpoint jest pomocny do:
 * - diagnostyki,
 * - health checków,
 * - debugowania routingu,
 * - sprawdzenia, czy request trafia do właściwego regionu.
 *
 * Kontroler nie podejmuje decyzji o zapisie/odczycie.
 * Do tego służy RegionGuardService.
 */
@RestController
@RequestMapping("/api/v1/region")
public class RegionController {

    /**
     * Serwis pilnujący konfiguracji regionu.
     *
     * RegionGuardService wie:
     * - jaki jest aktualny region aplikacji,
     * - czy ten region może obsługiwać zapisy,
     * - czy instancja działa jako read replica.
     *
     * Ten kontroler używa go tylko do odczytania currentRegion.
     */
    private final RegionGuardService regionGuardService;

    /**
     * Wstrzyknięcie serwisu regionu.
     */
    public RegionController(RegionGuardService regionGuardService) {
        this.regionGuardService = regionGuardService;
    }

    /**
     * Zwraca aktualny region aplikacji.
     *
     * Endpoint:
     * GET /api/v1/region
     *
     * Przykładowa odpowiedź:
     *
     * {
     *   "currentRegion": "eu-central-1"
     * }
     *
     * Dzięki temu klient, load balancer albo operator może sprawdzić,
     * która instancja aplikacji obsłużyła request.
     */
    @GetMapping
    public Map<String, String> region() {
        /*
         * Zwracamy prostą mapę zamiast osobnego DTO,
         * bo endpoint jest diagnostyczny i ma tylko jedno pole.
         */
        return Map.of(
                "currentRegion",
                regionGuardService.currentRegion()
        );
    }
}