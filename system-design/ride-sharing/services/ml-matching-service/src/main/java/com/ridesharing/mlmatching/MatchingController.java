package com.ridesharing.mlmatching;

import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

/**
 * Kontroler HTTP dla ML Matching Service.
 *
 * W Etapie 4 ten endpoint odpowiada za ranking kandydatów kierowców
 * dla konkretnego przejazdu. Nie wykonuje jeszcze rezerwacji kierowcy
 * ani nie zmienia statusu ride — tylko zwraca ranking.
 *
 * W aplikacji ride-sharing ML matching może uwzględniać:
 * - ETA kierowcy do pickup pointu,
 * - dystans,
 * - rating kierowcy,
 * - acceptance rate,
 * - cancellation rate,
 * - typ pojazdu,
 * - świeżość lokalizacji,
 * - fraud/risk score,
 * - historyczną skuteczność podobnych dopasowań,
 * - balans podaży i popytu w mieście.
 *
 * Controller pozostaje cienką warstwą REST:
 * przyjmuje request, waliduje go i deleguje ranking do MatchingModelService.
 */
@RestController
@RequestMapping("/api/v1/ml/matching")
public class MatchingController {

    /**
     * Serwis modelu rankingowego.
     *
     * To tutaj powinna znajdować się właściwa logika:
     * - feature engineering,
     * - scoring kandydatów,
     * - sortowanie wyników,
     * - wersjonowanie modelu,
     * - fallback, jeśli model ML jest niedostępny.
     */
    private final MatchingModelService service;

    /**
     * Konstruktor wstrzykujący MatchingModelService.
     *
     * Controller nie tworzy modelu ani nie liczy rankingu samodzielnie.
     */
    public MatchingController(MatchingModelService service) {
        this.service = service;
    }

    /**
     * Rankinguje kandydatów kierowców dla danego requestu matchingu.
     *
     * Endpoint:
     * POST /api/v1/ml/matching/rank
     *
     * Request powinien zawierać m.in.:
     * - rideId,
     * - cityId,
     * - pickup location albo H3 cell,
     * - listę kandydatów kierowców,
     * - cechy każdego kandydata, np. ETA, dystans, rating, status, acceptance rate.
     *
     * @Valid uruchamia walidację MatchingRankRequest.
     *
     * Response powinien zwrócić posortowaną listę kandydatów,
     * najczęściej od najlepszego do najsłabszego.
     *
     * Ważne: wynik rankingu nie oznacza jeszcze przypisania kierowcy.
     * Core Matching/Ride Service nadal musi wykonać atomową rezerwację kierowcy,
     * np. przez lock albo compare-and-set na statusie kierowcy.
     */
    @PostMapping("/rank")
    public MatchingRankResponse rank(@Valid @RequestBody MatchingRankRequest request) {
        return service.rank(request);
    }
}