package pl.jakubtworek.backend_engineering.stage_2.block_b.testing.events;

/**
 * Test event emitted when payment has failed.
 *
 * This event is commonly used to verify compensation behavior.
 */
public record PaymentFailedTestEvent(
        TestEventMetadata metadata,
        String orderId,
        String paymentId,
        String reason
) implements TestDomainEvent {

    public static final String TYPE = "PaymentFailed";

    @Override
    public String aggregateId() {
        return orderId;
    }

    @Override
    public String eventType() {
        return TYPE;
    }
}