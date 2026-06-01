package pl.jakubtworek.marketplace.integration.outbox;

import java.time.Instant;
import java.util.UUID;

public record OutboxEvent(
        UUID id,
        UUID aggregateId,
        String aggregateType,
        String eventType,
        int eventVersion,
        String payload,
        UUID correlationId,
        UUID causationId,
        OutboxEventStatus status,
        Instant createdAt,
        Instant publishedAt,
        int retryCount,
        String lastError
) {}
