package pl.jakubtworek.backend_engineering.stage_2.block_b.consumer;

/**
 * Base interface for all events consumed by this service.
 *
 * A consumer should not assume that every event is unique just because it was received once.
 * In at-least-once delivery systems, the same event may be delivered multiple times.
 */
public interface ConsumedEvent {

    /**
     * Returns common event metadata.
     */
    EventMetadata metadata();

    /**
     * Returns the business key associated with this event.
     *
     * For order workflows, this is usually orderId.
     */
    String aggregateId();

    /**
     * Returns the logical event type.
     */
    String eventType();
}