package pl.jakubtworek.backend_engineering.stage_2.block_a.integration.inventory.listener;

import pl.jakubtworek.backend_engineering.stage_2.block_a.integration.inventory.application.InventoryReservationService;
import pl.jakubtworek.backend_engineering.stage_2.block_a.integration.shared.event.OrderPlacedEvent;

// Choreography handler.
// Inventory reacts to Sales event and manages its own local state.
public final class ReserveInventoryOnOrderPlaced {

    private final InventoryReservationService reservationService;

    public ReserveInventoryOnOrderPlaced(InventoryReservationService reservationService) {
        this.reservationService = reservationService;
    }

    public void handle(OrderPlacedEvent event, String correlationId) {
        reservationService.reserve(event, correlationId);
    }
}