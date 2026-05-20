package pl.jakubtworek.backend_engineering.stage_2.block_a.ddd_strategy.shared.integration;

// Contract publisher used by upstream contexts.
// The implementation may use Kafka, RabbitMQ, REST callbacks, or another mechanism.
public interface IntegrationEventPublisher {

    void publish(IntegrationEvent event);
}