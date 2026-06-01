package pl.jakubtworek.marketplace.inventory.domain;

import pl.jakubtworek.marketplace.shared.kernel.DomainEvent;

import java.time.Instant;
import java.util.UUID;

public record StockReserved(UUID eventId, UUID aggregateId, UUID orderId, Instant occurredAt, UUID correlationId, UUID causationId) implements DomainEvent {
    public static StockReserved now(UUID productId, UUID orderId, UUID correlationId, UUID causationId) {
        return new StockReserved(UUID.randomUUID(), productId, orderId, Instant.now(), correlationId, causationId);
    }
    @Override public String eventType() { return "StockReserved"; }
    @Override public int eventVersion() { return 1; }
}
