package pl.jakubtworek.backend_engineering.stage_2.block_b.testing.events;

/**
 * Base interface for events used in integration and unit tests.
 *
 * Test events should behave like production events: immutable, uniquely identified,
 * and traceable by correlationId.
 */
public interface TestDomainEvent {

    /**
     * Returns event metadata.
     */
    TestEventMetadata metadata();

    /**
     * Returns the aggregate identifier, usually orderId.
     */
    String aggregateId();

    /**
     * Returns the logical event type.
     */
    String eventType();
}