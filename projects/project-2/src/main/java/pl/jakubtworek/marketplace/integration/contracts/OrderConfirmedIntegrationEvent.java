package pl.jakubtworek.marketplace.integration.contracts;

import pl.jakubtworek.marketplace.shared.kernel.DomainEvent;
import java.time.Instant;
import java.util.UUID;

/** Minimalny publiczny fakt potwierdzenia zamówienia. */
public record OrderConfirmedIntegrationEvent(UUID eventId, UUID aggregateId, Instant occurredAt,
        UUID correlationId, UUID causationId) implements DomainEvent {
    @Override public String eventType() { return "OrderConfirmed"; }
    @Override public int eventVersion() { return 1; }
}
