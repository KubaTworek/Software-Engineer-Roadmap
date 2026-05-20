package pl.jakubtworek.backend_engineering.stage_2.block_a.integration.shared.event;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

// Event published by Sales after an order is placed.
// Downstream contexts should not depend on the internal Sales domain model.
public record OrderPlacedEvent(
        String eventId,
        String orderId,
        String customerId,
        List<OrderItemPayload> items,
        BigDecimal totalAmount,
        String currency,
        Instant occurredAt
) implements IntegrationEvent {

    @Override
    public String eventType() {
        return "OrderPlaced";
    }

    @Override
    public int version() {
        return 1;
    }

    @Override
    public String aggregateId() {
        return orderId;
    }
}