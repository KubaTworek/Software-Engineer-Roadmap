package pl.jakubtworek.backend_engineering.stage_2.block_a.use_case.infrastructure.outbox;

import pl.jakubtworek.backend_engineering.stage_2.block_a.use_case.application.port.DomainEventPublisher;
import pl.jakubtworek.backend_engineering.stage_2.block_a.use_case.domain.event.DomainEvent;
import pl.jakubtworek.backend_engineering.stage_2.block_a.use_case.domain.event.OrderPlacedEvent;

import java.time.Instant;
import java.util.UUID;

// Domain event publisher implemented with the transactional outbox pattern.
// Instead of sending immediately to Kafka, it stores the event in the outbox table.
public final class OutboxEventPublisher implements DomainEventPublisher {

    private final OutboxMessageRepository outboxRepository;
    private final DomainEventSerializer serializer;

    public OutboxEventPublisher(
            OutboxMessageRepository outboxRepository,
            DomainEventSerializer serializer
    ) {
        this.outboxRepository = outboxRepository;
        this.serializer = serializer;
    }

    @Override
    public void publish(DomainEvent event) {
        OutboxMessage message = new OutboxMessage(
                UUID.randomUUID().toString(),
                extractAggregateId(event),
                event.eventType(),
                serializer.serialize(event),
                Instant.now()
        );

        outboxRepository.save(message);
    }

    private String extractAggregateId(DomainEvent event) {
        if (event instanceof OrderPlacedEvent orderPlaced) {
            return orderPlaced.orderId().value();
        }

        throw new IllegalArgumentException("Unsupported event type: " + event.eventType());
    }
}