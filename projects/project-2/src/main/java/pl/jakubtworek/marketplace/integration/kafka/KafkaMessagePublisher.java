package pl.jakubtworek.marketplace.integration.kafka;

public interface KafkaMessagePublisher {
    void publish(String topic, String key, IntegrationEventEnvelope envelope);
}
