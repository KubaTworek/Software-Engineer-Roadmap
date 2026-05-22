package pl.jakubtworek.backend_engineering.stage_2.block_b.kafka.producer;

/**
 * Simplified Kafka publisher.
 *
 * This class shows the important producer rule: publish with a stable key.
 */
public class KafkaEventPublisher implements EventPublisher {

    /**
     * Publishes the message to Kafka.
     *
     * In production, this method would serialize the payload and call KafkaProducer.send().
     */
    @Override
    public <T> void publish(KafkaEventMessage<T> message) {
        System.out.println(
                "Publishing to topic=" + message.topic().topicName()
                        + ", key=" + message.key().value()
                        + ", payload=" + message.payload()
        );
    }
}