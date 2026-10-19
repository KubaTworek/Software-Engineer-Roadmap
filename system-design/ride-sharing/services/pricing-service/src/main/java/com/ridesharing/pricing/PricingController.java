package com.ridesharing.pricing;

import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

/**
 * Kontroler HTTP dla osobnego Pricing Service.
 *
 * W architekturze ride-sharing Pricing Service odpowiada za wycenę przejazdu
 * poza głównym core-api. Dzięki temu logika cen może rozwijać się niezależnie
 * od logiki przejazdów, matchingu i płatności.
 *
 * Ten controller jest cienką warstwą API:
 * - przyjmuje request wyceny,
 * - uruchamia walidację,
 * - deleguje obliczenia do PricingService,
 * - zwraca wynik do klienta lub innego serwisu.
 */
@RestController
@RequestMapping("/api/v1/pricing")
public class PricingController {

    /**
     * Serwis zawierający właściwą logikę pricingu.
     *
     * To tutaj powinny znajdować się reguły:
     * - cena bazowa,
     * - stawka za kilometr,
     * - stawka za minutę,
     * - surge multiplier,
     * - waluta,
     * - typ pojazdu,
     * - reguły per miasto.
     */
    private final PricingService pricingService;

    public PricingController(PricingService pricingService) {
        this.pricingService = pricingService;
    }

    /**
     * Wylicza estymowaną cenę przejazdu.
     *
     * Endpoint:
     * POST /api/v1/pricing/estimate
     *
     * Request zawiera dane potrzebne do wyceny:
     * - cityId,
     * - vehicleType,
     * - distanceKm,
     * - durationMinutes,
     * - aktualny popyt,
     * - liczbę dostępnych kierowców.
     * Surge oblicza PricingService z popytu i podaży; klient nie podaje mnożnika.
     *
     * @Valid uruchamia walidację pól z PriceEstimateRequest.
     *
     * Wynik jest estymacją, a nie koniecznie finalną ceną rozliczeniową.
     * Finalna cena może zostać przeliczona po zakończeniu kursu.
     */
    @PostMapping("/estimate")
    PriceEstimateResponse estimate(@Valid @RequestBody PriceEstimateRequest request) {
        return pricingService.estimate(request);
    }
}
