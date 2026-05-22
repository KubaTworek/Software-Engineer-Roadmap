package pl.jakubtworek.backend_engineering.stage_2.block_a.ddd_tactic.infrastructure.messaging;

import pl.jakubtworek.backend_engineering.stage_2.block_a.ddd_tactic.infrastructure.persistance.OutboxMessage;

// Abstraction over an external broker.
// The implementation may use Kafka, RabbitMQ, AWS SNS/SQS, or another technology.
public interface ExternalMessageBroker {

    void publish(OutboxMessage message);
}