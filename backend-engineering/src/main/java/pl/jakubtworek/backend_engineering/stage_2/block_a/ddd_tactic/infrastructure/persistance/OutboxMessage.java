package pl.jakubtworek.backend_engineering.stage_2.block_a.ddd_tactic.infrastructure.persistance;

import java.time.Instant;

// Outbox entry stored in the same transaction as the aggregate.
// It prevents losing events when the database commit succeeds but message publishing fails.
public final class OutboxMessage {

    private final String id;
    private final String aggregateId;
    private final String eventType;
    private final String payload;
    private final Instant createdAt;
    private boolean published;

    public OutboxMessage(
            String id,
            String aggregateId,
            String eventType,
            String payload,
            Instant createdAt
    ) {
        this.id = id;
        this.aggregateId = aggregateId;
        this.eventType = eventType;
        this.payload = payload;
        this.createdAt = createdAt;
        this.published = false;
    }

    public void markAsPublished() {
        this.published = true;
    }

    public boolean isPublished() {
        return published;
    }
}