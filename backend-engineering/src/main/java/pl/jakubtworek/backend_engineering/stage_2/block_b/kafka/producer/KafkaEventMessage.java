package pl.jakubtworek.backend_engineering.stage_2.block_b.kafka.producer;

import pl.jakubtworek.backend_engineering.stage_2.block_b.kafka.partitioning.KafkaTopic;
import pl.jakubtworek.backend_engineering.stage_2.block_b.kafka.partitioning.MessageKey;

/**
 * Represents a message prepared for publication to Kafka.
 *
 * The key is especially important because Kafka uses it to choose the partition.
 */
public record KafkaEventMessage<T>(
        KafkaTopic topic,
        MessageKey key,
        T payload
) {
}