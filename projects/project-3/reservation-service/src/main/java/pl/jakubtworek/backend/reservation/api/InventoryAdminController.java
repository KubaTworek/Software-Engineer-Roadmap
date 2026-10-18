package pl.jakubtworek.backend.reservation.api;

import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import pl.jakubtworek.backend.reservation.application.ReservationService;

import java.util.UUID;

/** Produkcyjna granica: endpoint musi być dostępny tylko dla uwierzytelnionej administracji. */
@RestController
@RequestMapping("/internal/inventory")
public class InventoryAdminController {
    private final ReservationService service;
    public InventoryAdminController(ReservationService service) { this.service = service; }

    @PutMapping("/{eventId}/capacity")
    InventoryCapacityResponse change(@PathVariable UUID eventId,
                                     @Valid @RequestBody ChangeCapacityRequest request) {
        return service.changeCapacity(eventId, request.expectedVersion(), request.newTotalTickets());
    }
}
