package com.ridesharing.fraud;

import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

/**
 * Kontroler HTTP dla Fraud Service.
 *
 * W architekturze ride-sharing Fraud Service ocenia ryzyko operacji,
 * zanim system podejmie decyzję biznesową albo oznaczy sprawę do review.
 *
 * Typowe przypadki użycia:
 * - ocena ryzyka zamówienia przejazdu,
 * - wykrywanie nadużyć promocyjnych,
 * - ocena ryzyka płatności,
 * - wykrywanie podejrzanych anulowań,
 * - wykrywanie GPS spoofingu,
 * - oznaczanie podejrzanych kont lub przejazdów.
 *
 * Controller nie powinien zawierać reguł fraudowych.
 * Jego zadaniem jest przyjęcie requestu, walidacja i delegacja do FraudService.
 */
@RestController
@RequestMapping("/api/v1/fraud")
public class FraudController {

    /**
     * Serwis zawierający właściwą logikę oceny ryzyka.
     *
     * To tutaj powinny znajdować się:
     * - reguły scoringowe,
     * - integracje z feature store,
     * - modele ML,
     * - progi decyzji ALLOW / REVIEW / BLOCK,
     * - logika budowania powodów ryzyka.
     */
    private final FraudService fraudService;

    /**
     * Konstruktor wstrzykujący FraudService.
     *
     * Controller pozostaje cienką warstwą REST i nie tworzy serwisu samodzielnie.
     */
    public FraudController(FraudService fraudService) {
        this.fraudService = fraudService;
    }

    /**
     * Ocenia ryzyko operacji.
     *
     * Endpoint:
     * POST /api/v1/fraud/assess
     *
     * Request może zawierać np.:
     * - passengerId,
     * - driverId,
     * - rideId,
     * - cityId,
     * - kwotę płatności,
     * - metodę płatności,
     * - liczbę anulowań,
     * - sygnały urządzenia,
     * - sygnały lokalizacyjne.
     *
     * @Valid uruchamia walidację pól z RiskAssessmentRequest.
     *
     * Response powinien zwrócić:
     * - riskScore,
     * - decision, np. ALLOW / REVIEW / BLOCK,
     * - listę powodów,
     * - timestamp oceny.
     */
    @PostMapping("/assess")
    RiskAssessmentResponse assess(@Valid @RequestBody RiskAssessmentRequest request) {
        return fraudService.assess(request);
    }
}