package pl.jakubtworek.marketplace.ordering.domain;

import pl.jakubtworek.marketplace.shared.kernel.DomainEvent;
import pl.jakubtworek.marketplace.shared.kernel.Money;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record OrderPlaced(
        UUID eventId,
        UUID aggregateId,
        UUID customerId,
        Money total,
        List<Line> lines,
        Instant occurredAt,
        UUID correlationId,
        UUID causationId
) implements DomainEvent {
    public static OrderPlaced now(Order order, UUID correlationId, UUID causationId) {
        List<Line> lines = order.lines().stream()
                .map(line -> new Line(line.productId().value(), line.quantity(), line.unitPrice()))
                .toList();
        return new OrderPlaced(
                UUID.randomUUID(),
                order.id().value(),
                order.customerId().value(),
                order.total(),
                lines,
                Instant.now(),
                correlationId,
                causationId
        );
    }

    public record Line(UUID productId, int quantity, Money unitPrice) {}

    @Override public String eventType() { return "OrderPlaced"; }
    @Override public int eventVersion() { return 2; }
}
