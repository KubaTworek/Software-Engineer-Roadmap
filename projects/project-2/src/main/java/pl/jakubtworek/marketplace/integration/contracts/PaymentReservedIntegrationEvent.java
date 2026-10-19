package pl.jakubtworek.marketplace.integration.contracts;

import pl.jakubtworek.marketplace.shared.kernel.DomainEvent;
import java.time.Instant;
import java.util.UUID;

/** Opublikowany fakt Payment; nie ujawnia jego agregatu ani klas domenowych. */
public record PaymentReservedIntegrationEvent(UUID eventId, UUID aggregateId, UUID orderId,
        Instant occurredAt, UUID correlationId, UUID causationId) implements DomainEvent {
    @Override public String eventType() { return "PaymentReserved"; }
    @Override public int eventVersion() { return 1; }
}
