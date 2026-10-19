package pl.jakubtworek.marketplace.integration.contracts;

import pl.jakubtworek.marketplace.shared.kernel.DomainEvent;
import java.time.Instant;
import java.util.UUID;

/** Opublikowany fakt nieudanej rezerwacji zapasu. */
public record StockReservationFailedIntegrationEvent(UUID eventId, UUID aggregateId, UUID orderId,
        UUID productId, String reason, Instant occurredAt, UUID correlationId, UUID causationId)
        implements DomainEvent {
    @Override public String eventType() { return "StockReservationFailed"; }
    @Override public int eventVersion() { return 1; }
}
