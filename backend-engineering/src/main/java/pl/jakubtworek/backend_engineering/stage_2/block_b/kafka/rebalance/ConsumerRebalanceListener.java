package pl.jakubtworek.backend_engineering.stage_2.block_b.kafka.rebalance;

import pl.jakubtworek.backend_engineering.stage_2.block_b.kafka.consumer.TopicPartitionAssignment;

import java.util.List;

/**
 * Listener for Kafka consumer group rebalancing.
 *
 * Rebalancing happens when consumer instances join or leave a group,
 * or when topic partition counts change.
 */
public interface ConsumerRebalanceListener {

    /**
     * Called before partitions are taken away from this consumer.
     *
     * A consumer should finish or stop in-flight work and commit safe offsets
     * before losing partition ownership.
     */
    void onPartitionsRevoked(List<TopicPartitionAssignment> revokedPartitions);

    /**
     * Called after partitions are assigned to this consumer.
     *
     * The consumer may resume processing from the last committed offsets.
     */
    void onPartitionsAssigned(List<TopicPartitionAssignment> assignedPartitions);
}