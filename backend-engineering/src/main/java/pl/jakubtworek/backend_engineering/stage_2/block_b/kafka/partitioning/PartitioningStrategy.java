package pl.jakubtworek.backend_engineering.stage_2.block_b.kafka.partitioning;

/**
 * Describes the partitioning rule used by producers.
 *
 * The main rule is simple: all events for the same orderId must use
 * the same Kafka key.
 */
public interface PartitioningStrategy {

    /**
     * Resolves the Kafka message key for a business event.
     */
    MessageKey resolveKey(PartitionedEvent event);
}