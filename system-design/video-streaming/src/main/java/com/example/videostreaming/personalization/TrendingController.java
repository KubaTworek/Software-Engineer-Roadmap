package com.example.videostreaming.personalization;

import org.springframework.web.bind.annotation.*;

import static com.example.videostreaming.personalization.PersonalizationDtos.*;

/**
 * Kontroler trendujących treści.
 *
 * Główna odpowiedzialność:
 * - wystawia publiczny endpoint z popularnymi filmami,
 * - przyjmuje okno czasowe popularności,
 * - przyjmuje limit wyników,
 * - deleguje właściwe liczenie trendów do TrendingService.
 *
 * Ten kontroler nie liczy popularności samodzielnie.
 * Logika rankingu trending, scoringu i pobierania danych z feature store
 * albo warehouse znajduje się w TrendingService.
 */
@RestController
@RequestMapping("/api/trending")
public class TrendingController {

    /**
     * Serwis trendów.
     *
     * Odpowiada za:
     * - pobranie danych popularności,
     * - ograniczenie wyników do publicznych/opublikowanych filmów,
     * - policzenie albo pobranie trending score,
     * - sortowanie filmów według popularności,
     * - zbudowanie DTO odpowiedzi.
     */
    private final TrendingService trending;

    public TrendingController(TrendingService trending) {
        this.trending = trending;
    }

    /**
     * Zwraca listę trendujących filmów.
     *
     * Endpoint używany np. przez:
     * - sekcję "Popularne teraz",
     * - homepage,
     * - katalog,
     * - panel odkrywania treści.
     *
     * Parametry:
     * - windowHours: z ilu ostatnich godzin liczyć popularność,
     * - limit: maksymalna liczba wyników.
     *
     * Domyślnie:
     * - popularność liczona z ostatnich 24 godzin,
     * - zwracane maksymalnie 20 filmów.
     *
     * Ważne:
     * Kontroler nie powinien ufać limitowi bez kontroli.
     * TrendingService powinien dodatkowo obciąć limit do bezpiecznego maksimum,
     * np. 50 albo 100.
     */
    @GetMapping("/videos")
    public TrendingResponse videos(@RequestParam(defaultValue = "24") int windowHours,
                                   @RequestParam(defaultValue = "20") int limit) {
        return trending.trending(windowHours, limit);
    }
}