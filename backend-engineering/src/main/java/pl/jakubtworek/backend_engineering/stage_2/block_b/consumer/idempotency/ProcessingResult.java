package pl.jakubtworek.backend_engineering.stage_2.block_b.consumer.idempotency;

/**
 * Describes the result of event processing.
 *
 * The consumer loop can use this result to decide whether the Kafka offset
 * should be committed.
 */
public enum ProcessingResult {

    /**
     * The event was new and business processing completed successfully.
     */
    PROCESSED,

    /**
     * The event was already processed before and was safely skipped.
     */
    DUPLICATE_SKIPPED,

    /**
     * Processing failed with a retryable error.
     */
    RETRYABLE_FAILURE,

    /**
     * Processing failed with a non-retryable error and should go to DLQ.
     */
    NON_RETRYABLE_FAILURE
}