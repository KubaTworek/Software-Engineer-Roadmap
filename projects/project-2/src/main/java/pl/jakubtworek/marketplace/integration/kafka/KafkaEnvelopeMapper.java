package pl.jakubtworek.marketplace.integration.kafka;

import pl.jakubtworek.marketplace.integration.outbox.OutboxEvent;
import pl.jakubtworek.marketplace.integration.outbox.OutboxEventStatus;

public class KafkaEnvelopeMapper {
    public IntegrationEventEnvelope toEnvelope(OutboxEvent event) {
        return new IntegrationEventEnvelope(
                event.id(),
                event.aggregateId(),
                event.aggregateType(),
                event.eventType(),
                event.eventVersion(),
                event.payload(),
                event.correlationId(),
                event.causationId(),
                event.createdAt()
        );
    }

    public OutboxEvent toOutboxEvent(IntegrationEventEnvelope envelope) {
        return new OutboxEvent(
                envelope.eventId(),
                envelope.aggregateId(),
                envelope.aggregateType(),
                envelope.eventType(),
                envelope.eventVersion(),
                envelope.payload(),
                envelope.correlationId(),
                envelope.causationId(),
                OutboxEventStatus.NEW,
                envelope.occurredAt(),
                null,
                0,
                null
        );
    }
}
