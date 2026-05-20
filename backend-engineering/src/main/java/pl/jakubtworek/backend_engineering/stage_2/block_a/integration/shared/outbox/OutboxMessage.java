package pl.jakubtworek.backend_engineering.stage_2.block_a.integration.shared.outbox;

import java.time.Instant;

// Outbox record stored in the same database transaction as the business change.
// It prevents losing events when the service crashes after commit but before publishing.
public final class OutboxMessage {

    private final String id;
    private final String aggregateId;
    private final String eventType;
    private final int eventVersion;
    private final String payload;
    private final String correlationId;
    private final Instant createdAt;

    private int attempts;
    private boolean published;
    private Instant publishedAt;
    private String lastError;

    public OutboxMessage(
            String id,
            String aggregateId,
            String eventType,
            int eventVersion,
            String payload,
            String correlationId,
            Instant createdAt
    ) {
        this.id = id;
        this.aggregateId = aggregateId;
        this.eventType = eventType;
        this.eventVersion = eventVersion;
        this.payload = payload;
        this.correlationId = correlationId;
        this.createdAt = createdAt;
        this.attempts = 0;
        this.published = false;
    }

    // Marks the message as successfully published to the broker.
    public void markAsPublished(Instant publishedAt) {
        this.published = true;
        this.publishedAt = publishedAt;
        this.lastError = null;
    }

    // Records a failed publishing attempt.
    public void markAsFailed(String error) {
        this.attempts++;
        this.lastError = error;
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

    public int eventVersion() {
        return eventVersion;
    }

    public String payload() {
        return payload;
    }

    public String correlationId() {
        return correlationId;
    }

    public Instant createdAt() {
        return createdAt;
    }

    public int attempts() {
        return attempts;
    }

    public boolean published() {
        return published;
    }

    public Instant publishedAt() {
        return publishedAt;
    }

    public String lastError() {
        return lastError;
    }
}