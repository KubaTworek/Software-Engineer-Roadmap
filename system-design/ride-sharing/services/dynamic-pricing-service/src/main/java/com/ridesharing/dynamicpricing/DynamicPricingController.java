package com.ridesharing.dynamicpricing;

import org.springframework.web.bind.annotation.*;

/**
 * Kontroler HTTP dla Dynamic Pricing Service.
 *
 * W Etapie 4 ten endpoint odpowiada za dynamiczną wycenę przejazdu,
 * czyli cenę zależną nie tylko od trasy, ale też od aktualnej sytuacji rynkowej:
 * - popytu,
 * - dostępności kierowców,
 * - prognozy popytu,
 * - miasta,
 * - typu pojazdu,
 * - mnożnika surge,
 * - ograniczeń cenowych.
 *
 * Controller nie powinien zawierać logiki liczenia ceny.
 * Jego zadaniem jest przyjęcie requestu i delegacja do DynamicPricingService.
 */
@RestController
@RequestMapping("/api/v1/dynamic-pricing")
public class DynamicPricingController {

    /**
     * Serwis odpowiedzialny za właściwą logikę dynamic pricingu.
     *
     * To w nim powinny znajdować się:
     * - reguły surge,
     * - guardrails maksymalnej/minimalnej ceny,
     * - integracja z demand prediction,
     * - uwzględnienie podaży kierowców,
     * - ewentualny model ML.
     */
    private final DynamicPricingService service;

    /**
     * Konstruktor wstrzykujący DynamicPricingService.
     *
     * Controller pozostaje cienką warstwą REST i nie tworzy serwisu ręcznie.
     */
    public DynamicPricingController(DynamicPricingService service) {
        this.service = service;
    }

    /**
     * Wylicza dynamiczną cenę przejazdu.
     *
     * Endpoint:
     * POST /api/v1/dynamic-pricing/estimate
     *
     * Typowy request powinien zawierać:
     * - cityId,
     * - vehicleType,
     * - distanceKm,
     * - durationMinutes,
     * - activeRequests,
     * - availableDrivers,
     * - predictedDemand,
     * - basePrice albo dane taryfy.
     *
     * Wynik powinien zawierać:
     * - finalną estymowaną cenę,
     * - surge multiplier,
     * - breakdown ceny,
     * - walutę,
     * - expiresAt,
     * - ewentualne powody podbicia ceny.
     *
     * To nadal jest estimate. Finalne rozliczenie powinno zostać potwierdzone
     * po zakończeniu przejazdu przez payment/pricing flow.
     */
    @PostMapping("/estimate")
    public DynamicPriceResponse estimate(@RequestBody DynamicPriceRequest request) {
        return service.price(request);
    }
}