package pl.jakubtworek.backend_engineering.stage_2.block_b.testing.events;

import java.math.BigDecimal;

/**
 * Test event emitted when an order has been placed.
 *
 * This event is commonly used to test idempotency in Payment Service.
 */
public record OrderPlacedTestEvent(
        TestEventMetadata metadata,
        String orderId,
        BigDecimal totalAmount
) implements TestDomainEvent {

    public static final String TYPE = "OrderPlaced";

    @Override
    public String aggregateId() {
        return orderId;
    }

    @Override
    public String eventType() {
        return TYPE;
    }
}