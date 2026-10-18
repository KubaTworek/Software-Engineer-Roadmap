package pl.jakubtworek.marketplace.integration.contracts;

import pl.jakubtworek.marketplace.shared.kernel.DomainEvent;
import java.time.Instant;
import java.util.UUID;

public record StockReleasedIntegrationEvent(UUID eventId, UUID aggregateId, UUID orderId,
        Instant occurredAt, UUID correlationId, UUID causationId) implements DomainEvent {
    @Override public String eventType() { return "StockReleased"; }
    @Override public int eventVersion() { return 1; }
}
