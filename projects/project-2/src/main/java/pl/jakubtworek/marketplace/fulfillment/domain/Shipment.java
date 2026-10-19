package pl.jakubtworek.marketplace.fulfillment.domain;

import java.util.UUID;

/** Agregat fulfillmentu ma własną tożsamość i nie współdzieli agregatu Order. */
public record Shipment(UUID id, UUID orderId, FulfillmentStatus status) {
    public static Shipment start(UUID orderId) {
        return new Shipment(UUID.randomUUID(), orderId, FulfillmentStatus.PENDING);
    }
}
