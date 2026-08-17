package com.ridesharing.region;

import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Kontroler HTTP dla Region Control Plane.
 *
 * W Etapie 4 ten komponent reprezentuje warstwę routingu regionalnego,
 * potrzebną przy architekturze multi-region / active-active.
 *
 * W aplikacji ride-sharing routing regionalny może decydować:
 * - który region obsłuży dany przejazd,
 * - gdzie powinien trafić request użytkownika,
 * - który region jest właścicielem agregatu,
 * - jak ograniczać latency,
 * - jak reagować na awarie regionu,
 * - jak kierować ruch per cityId.
 *
 * Controller jest cienki: wystawia listę regionów i decyzję routingu,
 * ale sam nie zawiera logiki wyboru regionu.
 */
@RestController
@RequestMapping("/api/v1/regions")
public class RegionController {

    /**
     * Serwis zawierający właściwą logikę region control.
     *
     * To tutaj powinny być reguły:
     * - city -> primary region,
     * - fallback region,
     * - routing po aggregateId,
     * - routing po userRegionHint,
     * - status regionów,
     * - ewentualne przełączenie awaryjne.
     */
    private final RegionControlService service;

    /**
     * Konstruktor wstrzykujący RegionControlService.
     *
     * Controller nie podejmuje decyzji routingowych samodzielnie.
     */
    public RegionController(RegionControlService service) {
        this.service = service;
    }

    /**
     * Zwraca listę znanych regionów.
     *
     * Endpoint:
     * GET /api/v1/regions
     *
     * Response może zawierać np.:
     * - regionId,
     * - nazwę regionu,
     * - status,
     * - obsługiwane miasta,
     * - priority,
     * - endpoint bazowy.
     *
     * To jest endpoint diagnostyczny / control-plane,
     * nie główny endpoint domenowy aplikacji.
     */
    @GetMapping
    public List<RegionDescriptor> regions() {
        return service.regions();
    }

    /**
     * Zwraca decyzję routingu dla danego agregatu albo miasta.
     *
     * Endpoint:
     * GET /api/v1/regions/route
     *
     * Parametry:
     * - aggregateId: identyfikator obiektu domenowego, np. rideId/userId/paymentId,
     * - cityId: opcjonalne miasto, często najlepszy sygnał routingowy w ride-sharingu,
     * - userRegionHint: opcjonalna podpowiedź regionu użytkownika/klienta.
     *
     * Typowy wynik RoutingDecision powinien zawierać:
     * - targetRegion,
     * - reason,
     * - czy użyto fallbacku,
     * - ewentualny endpoint docelowy,
     * - informację o ownership.
     *
     * Ważne: routing regionalny nie rozwiązuje sam problemu spójności.
     * Jeśli dwa regiony mogą modyfikować ten sam ride, nadal potrzebujesz ownership,
     * conflict resolution albo single-writer-per-aggregate.
     */
    @GetMapping("/route")
    public RoutingDecision route(
            @RequestParam String aggregateId,
            @RequestParam(required = false) String cityId,
            @RequestParam(required = false) String userRegionHint
    ) {
        return service.route(
                aggregateId,
                cityId,
                userRegionHint
        );
    }
}