package pl.jakubtworek.backend_engineering.stage_2.block_b.testing.events;

/**
 * Test event emitted when an order has been cancelled.
 *
 * This event is expected as a compensation after failed payment.
 */
public record OrderCancelledTestEvent(
        TestEventMetadata metadata,
        String orderId,
        String reason
) implements TestDomainEvent {

    public static final String TYPE = "OrderCancelled";

    @Override
    public String aggregateId() {
        return orderId;
    }

    @Override
    public String eventType() {
        return TYPE;
    }
}