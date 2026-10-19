package pl.jakubtworek.backend_engineering.stage_2.block_b.kafka;

import org.junit.jupiter.api.Test;
import pl.jakubtworek.backend_engineering.stage_2.block_b.kafka.config.ConsumerConfiguration;
import pl.jakubtworek.backend_engineering.stage_2.block_b.kafka.config.TopicConfiguration;
import pl.jakubtworek.backend_engineering.stage_2.block_b.kafka.consumer.ConsumerAssignment;
import pl.jakubtworek.backend_engineering.stage_2.block_b.kafka.consumer.ConsumerGroup;
import pl.jakubtworek.backend_engineering.stage_2.block_b.kafka.consumer.KafkaRecordPosition;
import pl.jakubtworek.backend_engineering.stage_2.block_b.kafka.consumer.TopicPartitionAssignment;
import pl.jakubtworek.backend_engineering.stage_2.block_b.kafka.partitioning.KafkaTopic;
import pl.jakubtworek.backend_engineering.stage_2.block_b.kafka.partitioning.MessageKey;
import pl.jakubtworek.backend_engineering.stage_2.block_b.kafka.producer.KafkaEventMessage;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class KafkaConfigurationTest {

    @Test
    void shouldRejectInvalidKafkaCoordinatesAndConfiguration() {
        assertThrows(IllegalArgumentException.class, () -> new MessageKey(" "));
        assertThrows(IllegalArgumentException.class, () -> new ConsumerGroup(null));
        assertThrows(IllegalArgumentException.class, () -> new TopicPartitionAssignment("orders", -1));
        assertThrows(IllegalArgumentException.class, () -> new KafkaRecordPosition("orders", 0, -1));
        assertThrows(IllegalArgumentException.class, () ->
                new ConsumerConfiguration("localhost:9092", "payments", false, "invalid"));
        assertThrows(IllegalArgumentException.class, () ->
                new KafkaEventMessage<>(KafkaTopic.ORDERS, MessageKey.orderKey("O-1"), null));

        assertThrows(IllegalArgumentException.class, () -> new TopicConfiguration("orders", 0, (short) 1).validate());
    }

    @Test
    void shouldDefensivelyCopyPartitionAssignmentAndResolveOwnership() {
        List<TopicPartitionAssignment> source = new ArrayList<>();
        source.add(new TopicPartitionAssignment("orders", 0));
        ConsumerAssignment assignment = new ConsumerAssignment(
                "consumer-1", ConsumerGroup.paymentService(), source
        );

        source.add(new TopicPartitionAssignment("orders", 1));

        assertTrue(assignment.owns("orders", 0));
        assertFalse(assignment.owns("orders", 1));
        assertEquals(1, assignment.partitions().size());
    }

    @Test
    void shouldRejectDuplicatePartitionAssignmentAndOffsetOverflow() {
        TopicPartitionAssignment partition = new TopicPartitionAssignment("orders", 0);
        assertThrows(IllegalArgumentException.class, () -> new ConsumerAssignment(
                "consumer-1", ConsumerGroup.paymentService(), List.of(partition, partition)
        ));
        assertThrows(ArithmeticException.class, () ->
                new KafkaRecordPosition("orders", 0, Long.MAX_VALUE).nextOffset());
    }
}
