package pl.jakubtworek.backend_engineering.stage_2.block_b.consumer.dlq;

import pl.jakubtworek.backend_engineering.stage_2.block_b.consumer.ConsumedEvent;

/**
 * Kafka-based implementation of a dead-letter publisher.
 *
 * This is a simplified abstraction. A real implementation would use KafkaProducer
 * and publish to a topic such as orders.dlq or payments.dlq.
 */
public class KafkaDeadLetterPublisher<T extends ConsumedEvent>
        implements DeadLetterPublisher<T> {

    private final String deadLetterTopic;

    public KafkaDeadLetterPublisher(String deadLetterTopic) {
        this.deadLetterTopic = deadLetterTopic;
    }

    /**
     * Publishes the failed event to a DLQ topic.
     *
     * In a real implementation, the original payload, headers, topic, partition,
     * offset and error details should all be included.
     */
    @Override
    public void publish(T event, DeadLetterReason reason) {
        System.out.println("Publishing event "
                + event.metadata().eventId()
                + " to DLQ topic "
                + deadLetterTopic
                + " because: "
                + reason.message());
    }
}