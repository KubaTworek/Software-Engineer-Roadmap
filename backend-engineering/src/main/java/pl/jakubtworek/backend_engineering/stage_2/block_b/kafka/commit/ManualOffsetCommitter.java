package pl.jakubtworek.backend_engineering.stage_2.block_b.kafka.commit;

import pl.jakubtworek.backend_engineering.stage_2.block_b.kafka.consumer.KafkaRecordPosition;

/**
 * Manual offset committer.
 *
 * In production, this would call KafkaConsumer.commitSync() or commitAsync()
 * with the correct TopicPartition and OffsetAndMetadata.
 */
public class ManualOffsetCommitter implements OffsetCommitter {

    /**
     * Commits the offset after processing is complete.
     */
    @Override
    public void commit(KafkaRecordPosition position) {
        System.out.println(
                "Committing topic=" + position.topic()
                        + ", partition=" + position.partition()
                        + ", offset=" + position.nextOffset()
        );
    }
}