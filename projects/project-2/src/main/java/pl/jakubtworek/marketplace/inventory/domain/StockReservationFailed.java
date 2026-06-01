package pl.jakubtworek.marketplace.inventory.domain;

import pl.jakubtworek.marketplace.shared.kernel.DomainEvent;

import java.time.Instant;
import java.util.UUID;

public record StockReservationFailed(UUID eventId, UUID aggregateId, UUID orderId, String reason, Instant occurredAt, UUID correlationId, UUID causationId) implements DomainEvent {
    public static StockReservationFailed now(UUID productId, UUID orderId, String reason, UUID correlationId, UUID causationId) {
        return new StockReservationFailed(UUID.randomUUID(), productId, orderId, reason, Instant.now(), correlationId, causationId);
    }
    @Override public String eventType() { return "StockReservationFailed"; }
    @Override public int eventVersion() { return 1; }
}
