package pl.jakubtworek.backend_engineering.stage_2.block_a.integration.shared.event;

import java.time.Instant;

// Event published by Inventory when reservation cannot be completed.
public record InventoryReservationFailedEvent(
        String eventId,
        String orderId,
        String reason,
        Instant occurredAt
) implements IntegrationEvent {

    @Override
    public String eventType() {
        return "InventoryReservationFailed";
    }

    @Override
    public int version() {
        return 1;
    }

    @Override
    public String aggregateId() {
        return orderId;
    }
}