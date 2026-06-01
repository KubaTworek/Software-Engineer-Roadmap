package pl.jakubtworek.marketplace.ordering.domain;

import pl.jakubtworek.marketplace.shared.kernel.DomainEvent;

import java.time.Instant;
import java.util.UUID;

public record OrderCancelled(UUID eventId, UUID aggregateId, Instant occurredAt, UUID correlationId, UUID causationId) implements DomainEvent {
    public static OrderCancelled now(Order order, UUID correlationId, UUID causationId) {
        return new OrderCancelled(UUID.randomUUID(), order.id().value(), Instant.now(), correlationId, causationId);
    }
    @Override public String eventType() { return "OrderCancelled"; }
    @Override public int eventVersion() { return 1; }
}
