package pl.jakubtworek.marketplace.payment.domain;

import pl.jakubtworek.marketplace.shared.kernel.DomainEvent;
import java.time.Instant;
import java.util.UUID;

/** Fakt zakończenia kompensacji, publikowany tylko przy pierwszej zmianie stanu. */
public record PaymentReleased(UUID eventId, UUID aggregateId, UUID orderId, Instant occurredAt,
        UUID correlationId, UUID causationId) implements DomainEvent {
    public static PaymentReleased now(Payment payment, UUID correlationId, UUID causationId) {
        return new PaymentReleased(UUID.randomUUID(), payment.id().value(), payment.orderId(), Instant.now(), correlationId, causationId);
    }
    @Override public String eventType() { return "PaymentReleased"; }
    @Override public int eventVersion() { return 1; }
}
