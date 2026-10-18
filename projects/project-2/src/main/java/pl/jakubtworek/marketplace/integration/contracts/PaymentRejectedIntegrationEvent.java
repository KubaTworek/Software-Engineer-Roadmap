package pl.jakubtworek.marketplace.integration.contracts;

import pl.jakubtworek.marketplace.shared.kernel.DomainEvent;
import java.time.Instant;
import java.util.UUID;

/** Opublikowany fakt odrzucenia płatności, przeznaczony dla innych modułów. */
public record PaymentRejectedIntegrationEvent(UUID eventId, UUID aggregateId, UUID orderId, String reason,
        Instant occurredAt, UUID correlationId, UUID causationId) implements DomainEvent {
    @Override public String eventType() { return "PaymentRejected"; }
    @Override public int eventVersion() { return 1; }
}
