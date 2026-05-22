package pl.jakubtworek.backend_engineering.stage_2.block_b.kafka.commit;

/**
 * Describes the result of processing a consumed record.
 *
 * The result determines whether committing the offset is safe.
 */
public enum ProcessingOutcome {

    /**
     * Business logic completed successfully.
     */
    PROCESSED_SUCCESSFULLY(true),

    /**
     * The record was detected as a duplicate and skipped safely.
     */
    DUPLICATE_SKIPPED(true),

    /**
     * The record failed but was successfully moved to DLQ.
     */
    SENT_TO_DLQ(true),

    /**
     * Processing failed and the record should be retried later.
     */
    RETRY_REQUIRED(false);

    private final boolean canCommitOffset;

    ProcessingOutcome(boolean canCommitOffset) {
        this.canCommitOffset = canCommitOffset;
    }

    /**
     * Returns whether it is safe to commit the Kafka offset.
     */
    public boolean canCommitOffset() {
        return canCommitOffset;
    }
}