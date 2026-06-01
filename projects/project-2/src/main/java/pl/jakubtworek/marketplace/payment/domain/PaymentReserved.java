package pl.jakubtworek.marketplace.payment.domain;

import pl.jakubtworek.marketplace.shared.kernel.DomainEvent;

import java.time.Instant;
import java.util.UUID;

public record PaymentReserved(UUID eventId, UUID aggregateId, UUID orderId, Instant occurredAt, UUID correlationId, UUID causationId) implements DomainEvent {
    public static PaymentReserved now(Payment payment, UUID correlationId, UUID causationId) {
        return new PaymentReserved(UUID.randomUUID(), payment.id().value(), payment.orderId(), Instant.now(), correlationId, causationId);
    }
    @Override public String eventType() { return "PaymentReserved"; }
    @Override public int eventVersion() { return 1; }
}
