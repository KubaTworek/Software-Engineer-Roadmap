package pl.jakubtworek.backend_engineering.stage_2.block_b.kafka.example;

import pl.jakubtworek.backend_engineering.stage_2.block_b.kafka.events.OrderPlaced;
import pl.jakubtworek.backend_engineering.stage_2.block_b.kafka.partitioning.KafkaTopic;
import pl.jakubtworek.backend_engineering.stage_2.block_b.kafka.partitioning.OrderIdPartitioningStrategy;
import pl.jakubtworek.backend_engineering.stage_2.block_b.kafka.producer.KafkaEventMessage;
import pl.jakubtworek.backend_engineering.stage_2.block_b.kafka.producer.KafkaEventMessageFactory;
import pl.jakubtworek.backend_engineering.stage_2.block_b.kafka.producer.KafkaEventPublisher;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Example showing how to publish an event with orderId as the Kafka key.
 *
 * The key is what allows Kafka to place all events for the same order
 * in the same partition.
 */
public class ProducerExample {

    public static void main(String[] args) {
        OrderPlaced event = new OrderPlaced(
                UUID.randomUUID(),
                Instant.now(),
                "ORD-12345",
                "ORD-12345",
                new BigDecimal("159.99")
        );

        KafkaEventMessageFactory messageFactory =
                new KafkaEventMessageFactory(new OrderIdPartitioningStrategy());

        KafkaEventMessage<OrderPlaced> message = messageFactory.create(
                KafkaTopic.ORDERS,
                event
        );

        KafkaEventPublisher publisher = new KafkaEventPublisher();
        publisher.publish(message);
    }
}