package pl.jakubtworek.backend_engineering.stage_2.block_a.use_case.infrastructure.messaging;

// Abstraction over an external message broker.
// A real adapter may publish to Kafka, RabbitMQ, SNS, or another system.
public interface MessageBroker {

    void publish(String topic, String key, String payload);
}