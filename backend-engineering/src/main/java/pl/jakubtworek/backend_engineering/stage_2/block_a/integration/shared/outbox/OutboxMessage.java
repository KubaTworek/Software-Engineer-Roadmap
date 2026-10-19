package pl.jakubtworek.backend_engineering.stage_2.block_a.integration.shared.outbox;

import java.time.Instant;
import java.util.Objects;

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
        this.id = requireNonBlank(id, "id");
        this.aggregateId = requireNonBlank(aggregateId, "aggregateId");
        this.eventType = requireNonBlank(eventType, "eventType");
        if (eventVersion <= 0) {
            throw new IllegalArgumentException("eventVersion must be greater than zero");
        }
        this.eventVersion = eventVersion;
        this.payload = Objects.requireNonNull(payload, "payload must not be null");
        this.correlationId = requireNonBlank(correlationId, "correlationId");
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt must not be null");
        this.attempts = 0;
        this.published = false;
    }

    // Marks the message as successfully published to the broker.
    public void markAsPublished(Instant publishedAt) {
        Objects.requireNonNull(publishedAt, "publishedAt must not be null");
        if (publishedAt.isBefore(createdAt)) {
            throw new IllegalArgumentException("publishedAt must not be before createdAt");
        }
        this.published = true;
        this.publishedAt = publishedAt;
        this.lastError = null;
    }

    // Records a failed publishing attempt.
    public void markAsFailed(String error) {
        if (published) {
            throw new IllegalStateException("A published message cannot be marked as failed");
        }
        this.attempts++;
        this.lastError = requireNonBlank(error, "error");
    }

    private static String requireNonBlank(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value;
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
