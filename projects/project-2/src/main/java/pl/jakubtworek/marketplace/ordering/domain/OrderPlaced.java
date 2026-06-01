package pl.jakubtworek.marketplace.ordering.domain;

import pl.jakubtworek.marketplace.shared.kernel.DomainEvent;
import pl.jakubtworek.marketplace.shared.kernel.Money;

import java.time.Instant;
import java.util.UUID;

public record OrderPlaced(
        UUID eventId,
        UUID aggregateId,
        UUID customerId,
        Money total,
        Instant occurredAt,
        UUID correlationId,
        UUID causationId
) implements DomainEvent {
    public static OrderPlaced now(Order order, UUID correlationId, UUID causationId) {
        return new OrderPlaced(UUID.randomUUID(), order.id().value(), order.customerId().value(), order.total(), Instant.now(), correlationId, causationId);
    }

    @Override public String eventType() { return "OrderPlaced"; }
    @Override public int eventVersion() { return 1; }
}
