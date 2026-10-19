package pl.jakubtworek.backend_engineering.stage_2.block_a.integration.shared.outbox;

import pl.jakubtworek.backend_engineering.stage_2.block_a.integration.shared.messaging.EventEnvelope;
import pl.jakubtworek.backend_engineering.stage_2.block_a.integration.shared.messaging.MessageBroker;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;

// Background process that publishes pending outbox messages to the broker.
// It should be safe to retry because publishing may fail after the broker receives the message.
public final class OutboxRelay {

    private final OutboxMessageRepository outboxRepository;
    private final MessageBroker broker;

    public OutboxRelay(
            OutboxMessageRepository outboxRepository,
            MessageBroker broker
    ) {
        this.outboxRepository = Objects.requireNonNull(outboxRepository, "outboxRepository must not be null");
        this.broker = Objects.requireNonNull(broker, "broker must not be null");
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

                // A crash after publish and before this update causes another
                // delivery. Outbox provides at-least-once publication, so the
                // consumer still has to deduplicate by message/event id.
                message.markAsPublished(Instant.now());
                outboxRepository.update(message);
            } catch (Exception exception) {
                String error = exception.getMessage() == null
                        ? exception.getClass().getSimpleName()
                        : exception.getMessage();
                message.markAsFailed(error);
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
