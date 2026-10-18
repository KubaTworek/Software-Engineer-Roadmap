package pl.jakubtworek.marketplace.integration.contracts;

import pl.jakubtworek.marketplace.shared.kernel.DomainEvent;
import java.time.Instant;
import java.util.UUID;

/** Minimalny publiczny fakt anulowania zamówienia. */
public record OrderCancelledIntegrationEvent(UUID eventId, UUID aggregateId, Instant occurredAt,
        UUID correlationId, UUID causationId) implements DomainEvent {
    @Override public String eventType() { return "OrderCancelled"; }
    @Override public int eventVersion() { return 1; }
}
