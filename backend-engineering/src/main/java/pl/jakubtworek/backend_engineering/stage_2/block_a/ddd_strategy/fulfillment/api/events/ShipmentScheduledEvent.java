package pl.jakubtworek.backend_engineering.stage_2.block_a.ddd_strategy.fulfillment.api.events;

import pl.jakubtworek.backend_engineering.stage_2.block_a.ddd_strategy.shared.integration.IntegrationEvent;

import java.time.Instant;

// Event published by the Fulfillment context after shipment is scheduled.
public final class ShipmentScheduledEvent extends IntegrationEvent {

    private final String shipmentId;
    private final String orderId;
    private final String carrier;

    public ShipmentScheduledEvent(
            String eventId,
            Instant occurredAt,
            String shipmentId,
            String orderId,
            String carrier
    ) {
        super(eventId, "ShipmentScheduled", occurredAt);
        this.shipmentId = shipmentId;
        this.orderId = orderId;
        this.carrier = carrier;
    }

    public String shipmentId() {
        return shipmentId;
    }

    public String orderId() {
        return orderId;
    }

    public String carrier() {
        return carrier;
    }
}