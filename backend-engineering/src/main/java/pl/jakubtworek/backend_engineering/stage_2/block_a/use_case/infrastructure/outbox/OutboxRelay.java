package pl.jakubtworek.backend_engineering.stage_2.block_a.use_case.infrastructure.outbox;

import pl.jakubtworek.backend_engineering.stage_2.block_a.use_case.infrastructure.messaging.MessageBroker;

import java.util.List;

// Background relay responsible for sending outbox messages to the message broker.
// It should be idempotent because publishing can be retried.
public final class OutboxRelay {

    private final OutboxMessageRepository outboxRepository;
    private final MessageBroker broker;

    public OutboxRelay(
            OutboxMessageRepository outboxRepository,
            MessageBroker broker
    ) {
        this.outboxRepository = outboxRepository;
        this.broker = broker;
    }

    public void publishPendingMessages() {
        List<OutboxMessage> messages = outboxRepository.findUnpublished(100);

        for (OutboxMessage message : messages) {
            broker.publish(
                    topicFor(message.eventType()),
                    message.aggregateId(),
                    message.payload()
            );

            message.markAsPublished();
            outboxRepository.save(message);
        }
    }

    private String topicFor(String eventType) {
        return switch (eventType) {
            case "OrderPlaced" -> "sales.order-events";
            default -> throw new IllegalArgumentException("Unknown event type: " + eventType);
        };
    }
}