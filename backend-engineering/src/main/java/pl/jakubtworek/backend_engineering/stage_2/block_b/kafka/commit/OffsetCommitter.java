package pl.jakubtworek.backend_engineering.stage_2.block_b.kafka.commit;

import pl.jakubtworek.backend_engineering.stage_2.block_b.kafka.consumer.KafkaRecordPosition;

/**
 * Abstraction responsible for committing Kafka offsets.
 *
 * Offsets should be committed only after business side effects have been completed.
 */
public interface OffsetCommitter {

    /**
     * Commits the next offset for the given Kafka record position.
     */
    void commit(KafkaRecordPosition position);
}