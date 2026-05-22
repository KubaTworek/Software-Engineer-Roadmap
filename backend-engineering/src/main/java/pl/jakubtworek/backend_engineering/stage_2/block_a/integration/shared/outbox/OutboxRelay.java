package pl.jakubtworek.backend_engineering.stage_2.block_a.integration.shared.outbox;

import pl.jakubtworek.backend_engineering.stage_2.block_a.integration.shared.messaging.EventEnvelope;
import pl.jakubtworek.backend_engineering.stage_2.block_a.integration.shared.messaging.MessageBroker;

import java.time.Instant;
import java.util.List;
import java.util.Map;

// Background process that publishes pending outbox messages to the broker.
// It should be safe to retry because publishing may fail after the broker receives the message.
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
            try {
                EventEnvelope envelope = new EventEnvelope(
                        message.id(),
                        message.eventType(),
                        message.eventVersion(),
                        message.aggregateId(),
                        message.correlationId(),
                        null,
                        message.createdAt(),
                        Map.of("source", "sales"),
                        message.payload()
                );

                broker.publish(topicFor(message.eventType()), message.aggregateId(), envelope);

                message.markAsPublished(Instant.now());
                outboxRepository.update(message);
            } catch (Exception exception) {
                message.markAsFailed(exception.getMessage());
                outboxRepository.update(message);
            }
        }
    }

    private String topicFor(String eventType) {
        return switch (eventType) {
            case "OrderPlaced" -> "sales.order-events";
            case "PaymentCompleted", "PaymentFailed" -> "billing.payment-events";
            case "InventoryReserved", "InventoryReservationFailed" -> "inventory.reservation-events";
            default -> throw new IllegalArgumentException("Unknown event type: " + eventType);
        };
    }
}