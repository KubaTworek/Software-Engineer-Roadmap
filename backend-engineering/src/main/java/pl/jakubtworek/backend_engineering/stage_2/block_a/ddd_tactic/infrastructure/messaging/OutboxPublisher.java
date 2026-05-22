package pl.jakubtworek.backend_engineering.stage_2.block_a.ddd_tactic.infrastructure.messaging;

import pl.jakubtworek.backend_engineering.stage_2.block_a.ddd_tactic.infrastructure.persistance.OutboxMessage;

import java.util.List;

// Background component responsible for publishing outbox messages.
// It reads unpublished messages, sends them to the broker, and marks them as published.
public final class OutboxPublisher {

    private final OutboxMessageRepository outboxRepository;
    private final ExternalMessageBroker broker;

    public OutboxPublisher(
            OutboxMessageRepository outboxRepository,
            ExternalMessageBroker broker
    ) {
        this.outboxRepository = outboxRepository;
        this.broker = broker;
    }

    public void publishPendingMessages() {
        List<OutboxMessage> messages = outboxRepository.findUnpublished();

        for (OutboxMessage message : messages) {
            broker.publish(message);
            message.markAsPublished();
            outboxRepository.save(message);
        }
    }
}