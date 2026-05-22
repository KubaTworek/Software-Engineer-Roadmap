package pl.jakubtworek.backend_engineering.stage_2.block_a.ddd_tactic.infrastructure.messaging;

import pl.jakubtworek.backend_engineering.stage_2.block_a.ddd_tactic.application.port.DomainEventPublisher;
import pl.jakubtworek.backend_engineering.stage_2.block_a.ddd_tactic.domain.model.DomainEvent;

// Infrastructure adapter for publishing domain events.
// This class may delegate to Kafka, RabbitMQ, an outbox table, or another broker.
public final class MessageBrokerDomainEventPublisher implements DomainEventPublisher {

    @Override
    public void publish(DomainEvent event) {
        // Serialize the event and publish it to the configured message broker.
        // In a production system, this should usually be implemented with the outbox pattern.
    }
}