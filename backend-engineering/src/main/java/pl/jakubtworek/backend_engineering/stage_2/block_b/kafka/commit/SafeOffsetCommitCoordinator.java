package pl.jakubtworek.backend_engineering.stage_2.block_b.kafka.commit;

import pl.jakubtworek.backend_engineering.stage_2.block_b.kafka.consumer.KafkaRecordPosition;

/**
 * Guard that prevents committing offsets too early.
 *
 * This is a design-level protection: processing must be marked successful
 * before the offset can be committed.
 */
public class SafeOffsetCommitCoordinator {

    private final OffsetCommitter offsetCommitter;

    public SafeOffsetCommitCoordinator(OffsetCommitter offsetCommitter) {
        this.offsetCommitter = offsetCommitter;
    }

    /**
     * Commits the offset only when processing has completed safely.
     */
    public void commitAfterProcessing(
            KafkaRecordPosition position,
            ProcessingOutcome outcome
    ) {
        if (!outcome.canCommitOffset()) {
            throw new IllegalStateException(
                    "Offset cannot be committed before processing is safely completed."
            );
        }

        offsetCommitter.commit(position);
    }
}