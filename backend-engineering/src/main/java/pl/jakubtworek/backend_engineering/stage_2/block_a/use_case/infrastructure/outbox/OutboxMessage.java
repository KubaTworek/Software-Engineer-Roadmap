package pl.jakubtworek.backend_engineering.stage_2.block_a.use_case.infrastructure.outbox;

import java.time.Instant;

// Database representation of a message waiting to be published.
// It should be stored in the same transaction as the aggregate change.
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

    public String id() {
        return id;
    }

    public String aggregateId() {
        return aggregateId;
    }

    public String eventType() {
        return eventType;
    }

    public String payload() {
        return payload;
    }

    public Instant createdAt() {
        return createdAt;
    }

    public boolean published() {
        return published;
    }
}