package pl.jakubtworek.marketplace.inventory.domain;

import pl.jakubtworek.marketplace.shared.kernel.DomainEvent;
import java.time.Instant;
import java.util.UUID;

/** Fakt, że lokalna kompensacja Inventory została zatwierdzona. */
public record StockReleased(UUID eventId, UUID aggregateId, UUID orderId, Instant occurredAt,
        UUID correlationId, UUID causationId) implements DomainEvent {
    public static StockReleased now(UUID orderId, UUID correlationId, UUID causationId) {
        return new StockReleased(UUID.randomUUID(), orderId, orderId, Instant.now(), correlationId, causationId);
    }
    @Override public String eventType() { return "StockReleased"; }
    @Override public int eventVersion() { return 1; }
}
