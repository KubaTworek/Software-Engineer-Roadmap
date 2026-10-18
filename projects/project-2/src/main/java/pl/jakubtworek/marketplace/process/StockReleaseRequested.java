package pl.jakubtworek.marketplace.process;

import pl.jakubtworek.marketplace.shared.kernel.DomainEvent;
import java.time.Instant;
import java.util.UUID;

/** Komenda kompensacyjna; Inventory odtwarza linie z własnego rejestru rezerwacji. */
public record StockReleaseRequested(UUID eventId, UUID aggregateId, UUID orderId,
        Instant occurredAt, UUID correlationId, UUID causationId) implements DomainEvent {
    public static StockReleaseRequested now(UUID orderId, UUID correlationId, UUID causationId) {
        return new StockReleaseRequested(UUID.randomUUID(), orderId, orderId, Instant.now(), correlationId, causationId);
    }
    @Override public String eventType() { return "StockReleaseRequested"; }
    @Override public int eventVersion() { return 1; }
}
