package pl.jakubtworek.backend_engineering.stage_2.block_b.observability;

/**
 * Base interface for events that can be observed, logged and traced.
 *
 * Every event should expose metadata required for debugging and monitoring.
 */
public interface ObservableEvent {

    /**
     * Returns metadata used for correlation, tracing and diagnostics.
     */
    EventMetadata metadata();

    /**
     * Returns the business aggregate identifier.
     *
     * For an order workflow, this is usually orderId.
     */
    String aggregateId();

    /**
     * Returns the logical event type.
     */
    String eventType();
}