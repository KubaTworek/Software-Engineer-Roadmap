package pl.jakubtworek.backend_engineering.stage_2.block_b.consumer.kafka;

/**
 * Simplified offset committer.
 *
 * A production implementation would delegate to KafkaConsumer.commitSync
 * or KafkaConsumer.commitAsync with proper error handling.
 */
public class ManualOffsetCommitter implements OffsetCommitter {

    /**
     * Commits the next offset after processing.
     */
    @Override
    public void commit(KafkaRecordPosition position) {
        System.out.println("Committing offset "
                + position.nextOffset()
                + " for "
                + position.topic()
                + "-"
                + position.partition());
    }
}