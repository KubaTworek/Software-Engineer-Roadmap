package pl.jakubtworek.backend_engineering.stage_2.block_b.kafka.consumer;

import java.util.List;

/**
 * Represents the assignment of partitions to one consumer instance.
 *
 * Kafka manages such assignments automatically during group coordination
 * and rebalancing.
 */
public record ConsumerAssignment(
        String consumerInstanceId,
        ConsumerGroup consumerGroup,
        List<TopicPartitionAssignment> partitions
) {
    public ConsumerAssignment {
        if (consumerInstanceId == null || consumerInstanceId.isBlank()) {
            throw new IllegalArgumentException("Consumer instance id cannot be empty");
        }
        if (consumerGroup == null || partitions == null) {
            throw new IllegalArgumentException("Consumer group and partitions are required");
        }
        partitions = List.copyOf(partitions);
        if (partitions.stream().distinct().count() != partitions.size()) {
            throw new IllegalArgumentException("Partition assignment cannot contain duplicates");
        }
    }

    /**
     * Returns true when this consumer owns the selected partition.
     */
    public boolean owns(String topic, int partition) {
        return partitions.stream()
                .anyMatch(assigned ->
                        assigned.topic().equals(topic)
                                && assigned.partition() == partition
                );
    }
}
