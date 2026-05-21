package pl.jakubtworek.backend_engineering.stage_2.block_b.kafka.rebalance;

import pl.jakubtworek.backend_engineering.stage_2.block_b.kafka.consumer.TopicPartitionAssignment;

import java.util.List;

/**
 * Simple rebalance listener implementation.
 *
 * In a real application, this component would coordinate graceful shutdown
 * of in-flight processing and offset commits.
 */
public class LoggingRebalanceListener implements ConsumerRebalanceListener {

    /**
     * Handles partition revocation.
     */
    @Override
    public void onPartitionsRevoked(List<TopicPartitionAssignment> revokedPartitions) {
        System.out.println("Partitions revoked: " + revokedPartitions);
    }

    /**
     * Handles partition assignment.
     */
    @Override
    public void onPartitionsAssigned(List<TopicPartitionAssignment> assignedPartitions) {
        System.out.println("Partitions assigned: " + assignedPartitions);
    }
}