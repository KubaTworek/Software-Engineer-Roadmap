package pl.jakubtworek.backend_engineering.stage_2.block_b.kafka.consumer;

/**
 * Describes a Kafka topic partition assigned to a consumer instance.
 *
 * In a consumer group, each partition is processed by at most one active consumer
 * within that group at a given time.
 */
public record TopicPartitionAssignment(
        String topic,
        int partition
) {
}