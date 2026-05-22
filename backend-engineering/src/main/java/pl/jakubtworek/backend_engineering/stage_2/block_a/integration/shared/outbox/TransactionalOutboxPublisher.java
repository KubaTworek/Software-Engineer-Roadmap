package pl.jakubtworek.backend_engineering.stage_2.block_a.integration.shared.outbox;

import pl.jakubtworek.backend_engineering.stage_2.block_a.integration.shared.event.IntegrationEvent;
import pl.jakubtworek.backend_engineering.stage_2.block_a.integration.shared.messaging.EventSerializer;

import java.time.Instant;
import java.util.UUID;

// Publisher that stores events in the outbox table instead of sending them directly.
// It must be used inside the same transaction as the aggregate save.
public final class TransactionalOutboxPublisher {

    private final OutboxMessageRepository outboxRepository;
    private final EventSerializer serializer;

    public TransactionalOutboxPublisher(
            OutboxMessageRepository outboxRepository,
            EventSerializer serializer
    ) {
        this.outboxRepository = outboxRepository;
        this.serializer = serializer;
    }

    public void publish(IntegrationEvent event, String correlationId) {
        OutboxMessage message = new OutboxMessage(
                UUID.randomUUID().toString(),
                event.aggregateId(),
                event.eventType(),
                event.version(),
                serializer.serialize(event),
                correlationId,
                Instant.now()
        );

        outboxRepository.save(message);
    }
}