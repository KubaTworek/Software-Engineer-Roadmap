package pl.jakubtworek.backend_engineering.stage_2.block_a.ddd_tactic.application.port;

import pl.jakubtworek.backend_engineering.stage_2.block_a.ddd_tactic.domain.model.DomainEvent;

// Port for publishing domain events.
// Infrastructure may implement it using Kafka, RabbitMQ, outbox, or in-memory dispatching.
public interface DomainEventPublisher {

    void publish(DomainEvent event);
}