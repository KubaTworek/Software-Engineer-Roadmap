package pl.jakubtworek.backend_engineering.stage_2.block_a.integration.shared.messaging;

// Abstraction over a message broker.
// A real adapter may use Kafka, RabbitMQ, AWS SNS/SQS, or another technology.
public interface MessageBroker {

    void publish(String topic, String key, EventEnvelope envelope);
}