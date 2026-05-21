package pl.jakubtworek.backend_engineering.stage_2.block_b.kafka.config;

/**
 * Configuration for a Kafka consumer.
 *
 * enableAutoCommit should be false when the application needs to commit offsets
 * only after successful business processing.
 */
public record ConsumerConfiguration(
        String bootstrapServers,
        String groupId,
        boolean enableAutoCommit,
        String autoOffsetReset
) {
    /**
     * Creates a safe default configuration for manual commit consumers.
     */
    public static ConsumerConfiguration manualCommit(
            String bootstrapServers,
            String groupId
    ) {
        return new ConsumerConfiguration(
                bootstrapServers,
                groupId,
                false,
                "earliest"
        );
    }
}