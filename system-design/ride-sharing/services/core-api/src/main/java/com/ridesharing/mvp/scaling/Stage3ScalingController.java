package com.ridesharing.mvp.scaling;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Kontroler demonstracyjny dla Etapu 3 — Skalowanie.
 *
 * Jego zadaniem jest pokazanie, do którego shardu zostanie przypisane dane miasto.
 * W aplikacji ride-sharing sharding per city pozwala rozdzielać dane i obciążenie
 * według regionów operacyjnych, np. warsaw, krakow, berlin.
 *
 * Ten controller nie wykonuje jeszcze fizycznego routingu do osobnej bazy.
 * Pokazuje decyzję shardingu, którą później można wykorzystać w warstwie persistence,
 * konfiguracji datasource albo routingu requestów.
 */
@RestController
@RequestMapping("/api/v1/scaling")
public class Stage3ScalingController {

    /**
     * Komponent odpowiedzialny za mapowanie cityId na shard.
     *
     * Dzięki temu logika wyboru shardu nie jest zaszyta w controllerze.
     * Controller tylko przyjmuje request i zwraca wynik.
     */
    private final CityShardResolver resolver;

    /**
     * Konstruktor jawnie wstrzykuje CityShardResolver.
     *
     * Tutaj nie użyto Lomboka, więc zależność jest czytelna bez @RequiredArgsConstructor.
     */
    public Stage3ScalingController(CityShardResolver resolver) {
        this.resolver = resolver;
    }

    /**
     * Zwraca shard przypisany do danego miasta.
     *
     * Endpoint:
     * GET /api/v1/scaling/shard?cityId=warsaw
     *
     * Przykładowa odpowiedź:
     * {
     *   "cityId": "warsaw",
     *   "shard": "shard-eu-1"
     * }
     *
     * W kontekście ride-sharingu cityId jest naturalnym kluczem shardingu,
     * ponieważ większość przejazdów, kierowców i lokalizacji działa lokalnie
     * w ramach jednego miasta albo regionu.
     */
    @GetMapping("/shard")
    Map<String, String> shard(@RequestParam String cityId) {
        return Map.of(
                "cityId", cityId,
                "shard", resolver.resolve(cityId)
        );
    }
}