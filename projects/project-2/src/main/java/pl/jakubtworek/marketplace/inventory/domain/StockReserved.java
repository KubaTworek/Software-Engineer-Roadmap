package pl.jakubtworek.marketplace.inventory.domain;

import pl.jakubtworek.marketplace.shared.kernel.DomainEvent;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record StockReserved(
        UUID eventId,
        UUID aggregateId,
        UUID orderId,
        List<Line> lines,
        Instant occurredAt,
        UUID correlationId,
        UUID causationId
) implements DomainEvent {
    public static StockReserved now(UUID orderId, List<Line> lines, UUID correlationId, UUID causationId) {
        return new StockReserved(UUID.randomUUID(), orderId, orderId, List.copyOf(lines), Instant.now(), correlationId, causationId);
    }

    public record Line(UUID productId, int quantity) {}

    @Override public String eventType() { return "StockReserved"; }
    @Override public int eventVersion() { return 1; }
}
