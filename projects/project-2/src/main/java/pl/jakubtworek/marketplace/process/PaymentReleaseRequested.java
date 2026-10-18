package pl.jakubtworek.marketplace.process;

import pl.jakubtworek.marketplace.shared.kernel.DomainEvent;
import java.time.Instant;
import java.util.UUID;

/** Komenda kompensacyjna publikowana niezawodnie przez outbox procesu. */
public record PaymentReleaseRequested(UUID eventId, UUID aggregateId, UUID orderId,
        Instant occurredAt, UUID correlationId, UUID causationId) implements DomainEvent {
    public static PaymentReleaseRequested now(UUID orderId, UUID correlationId, UUID causationId) {
        return new PaymentReleaseRequested(UUID.randomUUID(), orderId, orderId, Instant.now(), correlationId, causationId);
    }
    @Override public String eventType() { return "PaymentReleaseRequested"; }
    @Override public int eventVersion() { return 1; }
}
