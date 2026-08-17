package com.ridesharing.location;

import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Kontroler HTTP dla osobnego Location Service.
 *
 * W Etapie 3 lokalizacja została wydzielona z core-api, ponieważ jest jednym
 * z najbardziej obciążonych elementów aplikacji ride-sharing.
 *
 * Ten serwis odpowiada za:
 * - przyjmowanie bieżącej lokalizacji kierowców,
 * - zapis live location,
 * - indeksowanie przestrzenne, np. H3/S2/Redis,
 * - wyszukiwanie kierowców w pobliżu punktu odbioru.
 *
 * Controller nie powinien zawierać logiki geospatial.
 * Jego zadaniem jest przyjęcie requestu i delegacja do LocationService.
 */
@RestController
@RequestMapping("/api/v1/locations")
public class LocationController {

    /**
     * Serwis zawierający właściwą logikę lokalizacji.
     *
     * To tutaj powinny znajdować się:
     * - zapis pozycji kierowcy,
     * - przeliczenie lat/lng na H3 cell,
     * - aktualizacja indeksu dostępnych kierowców,
     * - wyszukiwanie kandydatów dla matchingu.
     */
    private final LocationService locationService;

    /**
     * Konstruktor wstrzykujący LocationService.
     *
     * Controller pozostaje cienką warstwą REST.
     */
    public LocationController(LocationService locationService) {
        this.locationService = locationService;
    }

    /**
     * Aktualizuje bieżącą lokalizację kierowcy.
     *
     * Endpoint:
     * POST /api/v1/locations/drivers
     *
     * Typowy request powinien zawierać:
     * - driverId,
     * - cityId,
     * - lat/lng,
     * - heading,
     * - speed,
     * - availability/status,
     * - timestamp.
     *
     * Wynik DriverLocationSnapshot powinien zawierać zapisany snapshot lokalizacji,
     * np. driverId, cityId, lat/lng, h3Cell i updatedAt.
     *
     * To jest endpoint wysokiego ruchu — aplikacja kierowcy może wysyłać GPS co kilka sekund.
     * Dlatego logika pod spodem powinna być szybka i nie powinna zapisywać każdego punktu
     * synchronicznie do relacyjnej bazy.
     */
    @PostMapping("/drivers")
    DriverLocationSnapshot update(@Valid @RequestBody UpdateDriverLocationRequest request) {
        return locationService.update(request);
    }

    /**
     * Zwraca listę dostępnych kierowców w pobliżu wskazanego punktu.
     *
     * Endpoint:
     * GET /api/v1/locations/nearby-drivers
     *
     * Parametry:
     * - cityId: miasto/region operacyjny; ogranicza wyszukiwanie do właściwego shardu/rynku,
     * - lat/lng: punkt odniesienia, zwykle pickup pasażera,
     * - ringSize: zakres wyszukiwania po sąsiednich komórkach H3/S2,
     * - limit: maksymalna liczba kandydatów zwracanych do matchingu.
     *
     * Wynik nie oznacza jeszcze finalnego dopasowania kierowcy.
     * To tylko pula kandydatów dla Matching Service, który może potem zastosować ranking,
     * ETA, rating, typ pojazdu albo fraud score.
     */
    @GetMapping("/nearby-drivers")
    List<NearbyDriver> nearby(
            @RequestParam String cityId,
            @RequestParam double lat,
            @RequestParam double lng,
            @RequestParam(defaultValue = "2") int ringSize,
            @RequestParam(defaultValue = "20") int limit
    ) {
        return locationService.nearby(
                cityId,
                lat,
                lng,
                ringSize,
                limit
        );
    }
}