package pl.jakubtworek.backend_engineering.stage_2.block_b.kafka.producer;

import pl.jakubtworek.backend_engineering.stage_2.block_b.kafka.partitioning.KafkaTopic;
import pl.jakubtworek.backend_engineering.stage_2.block_b.kafka.partitioning.PartitionedEvent;
import pl.jakubtworek.backend_engineering.stage_2.block_b.kafka.partitioning.PartitioningStrategy;

/**
 * Factory responsible for preparing Kafka messages from domain events.
 *
 * It applies the partitioning strategy consistently for all event types.
 */
public class KafkaEventMessageFactory {

    private final PartitioningStrategy partitioningStrategy;

    public KafkaEventMessageFactory(PartitioningStrategy partitioningStrategy) {
        this.partitioningStrategy = partitioningStrategy;
    }

    /**
     * Creates a Kafka message for the selected topic.
     *
     * The key is resolved from the event, usually from orderId.
     */
    public <T extends PartitionedEvent> KafkaEventMessage<T> create(
            KafkaTopic topic,
            T event
    ) {
        return new KafkaEventMessage<>(
                topic,
                partitioningStrategy.resolveKey(event),
                event
        );
    }
}