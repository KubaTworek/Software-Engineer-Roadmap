package pl.jakubtworek.backend_engineering.stage_2.block_b.consumer.kafka;

/**
 * Abstraction responsible for committing Kafka offsets.
 *
 * Offset commit should happen only after the event has been processed successfully,
 * skipped as duplicate, or safely moved to DLQ.
 */
public interface OffsetCommitter {

    /**
     * Commits the next offset for the given record position.
     */
    void commit(KafkaRecordPosition position);
}