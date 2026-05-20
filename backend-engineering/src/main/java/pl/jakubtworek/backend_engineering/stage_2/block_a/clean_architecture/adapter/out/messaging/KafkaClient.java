package pl.jakubtworek.backend_engineering.stage_2.block_a.clean_architecture.adapter.out.messaging;

// Low-level Kafka abstraction.
// A real implementation may use KafkaTemplate or another broker client.
public interface KafkaClient {

    void send(String topic, Object message);
}