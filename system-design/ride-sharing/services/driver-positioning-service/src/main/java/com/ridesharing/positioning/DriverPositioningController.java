package com.ridesharing.positioning;

import org.springframework.web.bind.annotation.*;

/**
 * Kontroler HTTP dla rekomendacji pozycjonowania kierowców.
 *
 * W Etapie 4 ten endpoint reprezentuje warstwę driver positioning,
 * czyli podpowiadanie kierowcy, gdzie warto się przemieścić,
 * żeby zwiększyć szansę na szybkie otrzymanie kolejnego kursu.
 *
 * W aplikacji ride-sharing takie rekomendacje mogą bazować na:
 * - prognozie popytu,
 * - aktualnej podaży kierowców,
 * - odległości kierowcy od obszarów wysokiego popytu,
 * - historycznych hotspotach,
 * - eventach lokalnych,
 * - dynamic pricingu.
 *
 * Controller nie liczy rekomendacji samodzielnie.
 * Jego rola to przyjęcie parametrów i delegacja do DriverPositioningService.
 */
@RestController
@RequestMapping("/api/v1/positioning")
public class DriverPositioningController {

    /**
     * Serwis odpowiedzialny za właściwą logikę rekomendacji.
     *
     * To tutaj powinny znajdować się reguły lub model ML wybierający najlepszy obszar,
     * np. najbliższą komórkę H3 z wysokim przewidywanym popytem.
     */
    private final DriverPositioningService service;

    /**
     * Konstruktor wstrzykujący DriverPositioningService.
     *
     * Controller pozostaje cienką warstwą HTTP i nie tworzy serwisu ręcznie.
     */
    public DriverPositioningController(DriverPositioningService service) {
        this.service = service;
    }

    /**
     * Zwraca rekomendację relokacji dla konkretnego kierowcy.
     *
     * Endpoint:
     * GET /api/v1/positioning/recommendations
     *
     * Parametry:
     * - driverId: identyfikator kierowcy,
     * - cityId: miasto/rynek operacyjny,
     * - lat/lng: aktualna lokalizacja kierowcy.
     *
     * Przykład:
     * /api/v1/positioning/recommendations?driverId=d1&cityId=warsaw&lat=52.23&lng=21.01
     *
     * Wynik może zawierać np.:
     * - sugerowaną komórkę H3,
     * - opis hotspotu,
     * - przewidywany popyt,
     * - szacowany dystans do rekomendowanego obszaru,
     * - powód rekomendacji.
     */
    @GetMapping("/recommendations")
    public DriverPositioningResponse recommendations(
            @RequestParam String driverId,
            @RequestParam String cityId,
            @RequestParam double lat,
            @RequestParam double lng
    ) {
        return service.recommend(driverId, cityId, lat, lng);
    }
}