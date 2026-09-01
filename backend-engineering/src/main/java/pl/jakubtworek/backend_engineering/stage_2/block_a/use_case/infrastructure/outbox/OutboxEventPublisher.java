package pl.jakubtworek.backend_engineering.stage_2.block_a.use_case.infrastructure.outbox;

import pl.jakubtworek.backend_engineering.stage_2.block_a.use_case.application.port.DomainEventPublisher;
import pl.jakubtworek.backend_engineering.stage_2.block_a.use_case.domain.event.DomainEvent;
import pl.jakubtworek.backend_engineering.stage_2.block_a.use_case.integration.event.DomainEventToIntegrationEventMapper;
import pl.jakubtworek.backend_engineering.stage_2.block_a.use_case.integration.event.IntegrationEvent;

// Domain event publisher implemented with the transactional outbox pattern.
// Instead of sending immediately to Kafka, it stores the event in the outbox table.
public final class OutboxEventPublisher implements DomainEventPublisher {

    private final OutboxMessageRepository outboxRepository;
    private final DomainEventToIntegrationEventMapper eventMapper;
    private final IntegrationEventSerializer serializer;

    public OutboxEventPublisher(
            OutboxMessageRepository outboxRepository,
            DomainEventToIntegrationEventMapper eventMapper,
            IntegrationEventSerializer serializer
    ) {
        this.outboxRepository = outboxRepository;
        this.eventMapper = eventMapper;
        this.serializer = serializer;
    }

    @Override
    public void publish(DomainEvent event) {
        IntegrationEvent integrationEvent = eventMapper.map(event);
        OutboxMessage message = new OutboxMessage(
                integrationEvent.messageId(),
                integrationEvent.aggregateId(),
                integrationEvent.eventType(),
                serializer.serialize(integrationEvent),
                integrationEvent.occurredAt()
        );

        outboxRepository.save(message);
    }
}
