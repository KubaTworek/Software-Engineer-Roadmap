package pl.jakubtworek.backend_engineering.stage_2.block_b.kafka.partitioning;

/**
 * Base interface for events that should be partitioned by business key.
 *
 * Kafka ordering is guaranteed only inside one partition.
 * Therefore, events that must be processed in order should share the same key.
 */
public interface PartitionedEvent {

    /**
     * Returns the order identifier used as Kafka message key.
     */
    String orderId();

    /**
     * Returns the logical event type.
     */
    String eventType();
}