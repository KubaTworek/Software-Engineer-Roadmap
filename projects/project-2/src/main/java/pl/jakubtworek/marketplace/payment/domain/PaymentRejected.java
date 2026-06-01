package pl.jakubtworek.marketplace.payment.domain;

import pl.jakubtworek.marketplace.shared.kernel.DomainEvent;

import java.time.Instant;
import java.util.UUID;

public record PaymentRejected(UUID eventId, UUID aggregateId, UUID orderId, String reason, Instant occurredAt, UUID correlationId, UUID causationId) implements DomainEvent {
    public static PaymentRejected now(Payment payment, String reason, UUID correlationId, UUID causationId) {
        return new PaymentRejected(UUID.randomUUID(), payment.id().value(), payment.orderId(), reason, Instant.now(), correlationId, causationId);
    }
    @Override public String eventType() { return "PaymentRejected"; }
    @Override public int eventVersion() { return 1; }
}
