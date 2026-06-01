package pl.jakubtworek.marketplace.integration.kafka;

import java.time.Instant;
import java.util.UUID;

public record IntegrationEventEnvelope(
        UUID eventId,
        UUID aggregateId,
        String aggregateType,
        String eventType,
        int eventVersion,
        String payload,
        UUID correlationId,
        UUID causationId,
        Instant occurredAt
) {
}
