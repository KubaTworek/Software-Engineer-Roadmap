package pl.jakubtworek.marketplace.integration.contracts;

import pl.jakubtworek.marketplace.shared.kernel.DomainEvent;
import java.time.Instant;
import java.util.UUID;

public record StockReleaseRequestedIntegrationEvent(UUID eventId, UUID aggregateId, UUID orderId,
        Instant occurredAt, UUID correlationId, UUID causationId) implements DomainEvent {
    @Override public String eventType() { return "StockReleaseRequested"; }
    @Override public int eventVersion() { return 1; }
}
