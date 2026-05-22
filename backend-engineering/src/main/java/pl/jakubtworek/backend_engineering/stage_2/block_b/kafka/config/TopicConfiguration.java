package pl.jakubtworek.backend_engineering.stage_2.block_b.kafka.config;

/**
 * Configuration for a Kafka topic.
 *
 * The number of partitions determines the maximum parallelism for a consumer group.
 * However, increasing partitions can affect ordering assumptions if keying strategy
 * is not stable and well understood.
 */
public record TopicConfiguration(
        String topicName,
        int partitions,
        short replicationFactor
) {
    /**
     * Validates basic topic configuration.
     */
    public void validate() {
        if (partitions <= 0) {
            throw new IllegalArgumentException("Topic must have at least one partition.");
        }

        if (replicationFactor <= 0) {
            throw new IllegalArgumentException("Replication factor must be positive.");
        }
    }
}