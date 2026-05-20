package pl.jakubtworek.backend_engineering.stage_2.block_a.integration.shared.event;

import java.time.Instant;

// Event published by Inventory after products are reserved.
public record InventoryReservedEvent(
        String eventId,
        String orderId,
        String reservationId,
        Instant occurredAt
) implements IntegrationEvent {

    @Override
    public String eventType() {
        return "InventoryReserved";
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