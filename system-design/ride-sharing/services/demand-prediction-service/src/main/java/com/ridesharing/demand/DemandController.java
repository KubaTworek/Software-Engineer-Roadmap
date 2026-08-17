package com.ridesharing.demand;

import org.springframework.web.bind.annotation.*;

/**
 * Kontroler HTTP dla prognozowania popytu.
 *
 * W Etapie 4 ten endpoint reprezentuje warstwę demand prediction,
 * czyli przewidywanie, gdzie i kiedy pojawi się zapotrzebowanie na przejazdy.
 *
 * W aplikacji ride-sharing takie prognozy mogą zasilać:
 * - driver positioning,
 * - dynamic pricing,
 * - matching,
 * - planowanie podaży kierowców,
 * - dashboard operacyjny miasta.
 *
 * Controller jest cienki: przyjmuje parametry requestu i deleguje logikę
 * do DemandPredictionService.
 */
@RestController
@RequestMapping("/api/v1/demand")
public class DemandController {

    /**
     * Serwis odpowiedzialny za właściwą prognozę popytu.
     *
     * To tutaj powinna znajdować się logika baseline albo ML:
     * - dane historyczne,
     * - aktualny popyt,
     * - liczba aktywnych kierowców,
     * - pora dnia,
     * - dzień tygodnia,
     * - eventy lokalne,
     * - pogoda,
     * - cechy H3 cell.
     */
    private final DemandPredictionService service;

    /**
     * Konstruktor wstrzykujący DemandPredictionService.
     *
     * Controller nie tworzy serwisu samodzielnie, tylko korzysta z dependency injection.
     */
    public DemandController(DemandPredictionService service) {
        this.service = service;
    }

    /**
     * Zwraca prognozę popytu dla miasta i opcjonalnej komórki H3.
     *
     * Endpoint:
     * GET /api/v1/demand/forecast
     *
     * Parametry:
     * - cityId: wymagane miasto/rynek operacyjny, np. warsaw,
     * - h3Cell: opcjonalna komórka geograficzna dla dokładniejszej prognozy,
     * - horizonMinutes: horyzont prognozy w minutach, domyślnie 15.
     *
     * Przykład:
     * /api/v1/demand/forecast?cityId=warsaw&h3Cell=891e2040c37ffff&horizonMinutes=15
     *
     * W praktyce wynik może powiedzieć:
     * - ile requestów spodziewamy się w tej lokalizacji,
     * - jaki jest poziom popytu,
     * - czy warto przesunąć kierowców w ten obszar,
     * - czy dynamic pricing powinien podnieść mnożnik.
     */
    @GetMapping("/forecast")
    public DemandForecastResponse forecast(
            @RequestParam String cityId,
            @RequestParam(required = false) String h3Cell,
            @RequestParam(defaultValue = "15") int horizonMinutes
    ) {
        return service.forecast(cityId, h3Cell, horizonMinutes);
    }
}