package pl.jakubtworek.marketplace.integration.contracts;

import pl.jakubtworek.marketplace.shared.kernel.DomainEvent;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Opublikowany fakt Inventory; linie są kopią kontraktu, a nie encjami magazynu. */
public record StockReservedIntegrationEvent(UUID eventId, UUID aggregateId, UUID orderId, List<Line> lines,
        Instant occurredAt, UUID correlationId, UUID causationId) implements DomainEvent {
    public StockReservedIntegrationEvent { lines = lines == null ? List.of() : List.copyOf(lines); }
    public record Line(UUID productId, int quantity) {}
    @Override public String eventType() { return "StockReserved"; }
    @Override public int eventVersion() { return 1; }
}
