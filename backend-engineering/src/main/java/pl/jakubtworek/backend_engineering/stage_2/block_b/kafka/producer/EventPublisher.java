package pl.jakubtworek.backend_engineering.stage_2.block_b.kafka.producer;

/**
 * Abstraction over Kafka publishing.
 *
 * A real implementation would delegate to KafkaProducer or a framework
 * such as Spring Kafka.
 */
public interface EventPublisher {

    /**
     * Publishes a prepared Kafka event message.
     */
    <T> void publish(KafkaEventMessage<T> message);
}