package pl.jakubtworek.backend_engineering.stage_2.block_b.kafka.commit;

/**
 * Defines when offsets should be committed.
 */
public enum OffsetCommitMode {

    /**
     * Kafka or the framework commits offsets automatically.
     *
     * This is simpler, but dangerous for systems with business side effects,
     * because the offset may be committed before processing is complete.
     */
    AUTO_COMMIT,

    /**
     * The application commits offsets explicitly after successful processing.
     *
     * This is preferred for event-driven workflows that write to databases,
     * call external services or publish follow-up events.
     */
    MANUAL_COMMIT
}