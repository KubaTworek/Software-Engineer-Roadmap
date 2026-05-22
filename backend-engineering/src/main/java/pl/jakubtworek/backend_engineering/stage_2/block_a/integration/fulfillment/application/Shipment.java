package pl.jakubtworek.backend_engineering.stage_2.block_a.integration.fulfillment.application;

import java.time.Instant;
import java.util.UUID;

// Fulfillment aggregate or entity representing a shipment.
// It is owned by the Fulfillment context, not by Sales.
public final class Shipment {

    private final String shipmentId;
    private final String orderId;
    private final ShipmentStatus status;
    private final Instant scheduledAt;

    private Shipment(
            String shipmentId,
            String orderId,
            ShipmentStatus status,
            Instant scheduledAt
    ) {
        this.shipmentId = shipmentId;
        this.orderId = orderId;
        this.status = status;
        this.scheduledAt = scheduledAt;
    }

    public static Shipment scheduleForOrder(String orderId) {
        if (orderId == null || orderId.isBlank()) {
            throw new IllegalArgumentException("OrderId cannot be empty");
        }

        return new Shipment(
                "S-" + UUID.randomUUID(),
                orderId,
                ShipmentStatus.SCHEDULED,
                Instant.now()
        );
    }

    public String shipmentId() {
        return shipmentId;
    }

    public String orderId() {
        return orderId;
    }

    public ShipmentStatus status() {
        return status;
    }

    public Instant scheduledAt() {
        return scheduledAt;
    }
}