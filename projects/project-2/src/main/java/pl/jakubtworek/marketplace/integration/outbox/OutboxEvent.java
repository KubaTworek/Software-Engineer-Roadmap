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
) {
    public OutboxEvent markPublished(Instant publishedAt) {
        return new OutboxEvent(id, aggregateId, aggregateType, eventType, eventVersion, payload,
                correlationId, causationId, OutboxEventStatus.PUBLISHED, createdAt, publishedAt, retryCount, null);
    }

    public OutboxEvent markFailed(String reason) {
        return new OutboxEvent(id, aggregateId, aggregateType, eventType, eventVersion, payload,
                correlationId, causationId, OutboxEventStatus.FAILED, createdAt, publishedAt, retryCount + 1, reason);
    }

    public OutboxEvent markNewForRetry() {
        return new OutboxEvent(id, aggregateId, aggregateType, eventType, eventVersion, payload,
                correlationId, causationId, OutboxEventStatus.NEW, createdAt, publishedAt, retryCount, lastError);
    }
}
