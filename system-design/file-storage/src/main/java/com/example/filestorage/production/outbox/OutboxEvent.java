package com.example.filestorage.production.outbox;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "outbox_events")
public class OutboxEvent {
    @Id
    private UUID id;
    @Column(name = "event_type", nullable = false)
    private String eventType;
    @Column(name = "aggregate_type", nullable = false)
    private String aggregateType;
    @Column(name = "aggregate_id", nullable = false)
    private UUID aggregateId;
    @Column(nullable = false, columnDefinition = "TEXT")
    private String payload;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private OutboxStatus status;
    @Column(nullable = false)
    private int attempts;
    @Column(name = "last_error", columnDefinition = "TEXT")
    private String lastError;
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
    @Column(name = "published_at")
    private Instant publishedAt;

    protected OutboxEvent() {}

    public OutboxEvent(String eventType, String aggregateType, UUID aggregateId, String payload) {
        this.id = UUID.randomUUID();
        this.eventType = eventType;
        this.aggregateType = aggregateType;
        this.aggregateId = aggregateId;
        this.payload = payload == null ? "{}" : payload;
        this.status = OutboxStatus.PENDING;
        this.attempts = 0;
        this.createdAt = Instant.now();
    }

    public void markPublished() {
        this.status = OutboxStatus.PUBLISHED;
        this.publishedAt = Instant.now();
        this.lastError = null;
    }

    public void markFailed(String error) {
        this.status = OutboxStatus.FAILED;
        this.attempts++;
        this.lastError = error;
    }

    public void retryLater(String error) {
        this.status = OutboxStatus.PENDING;
        this.attempts++;
        this.lastError = error;
    }

    public UUID getId() { return id; }
    public String getEventType() { return eventType; }
    public String getAggregateType() { return aggregateType; }
    public UUID getAggregateId() { return aggregateId; }
    public String getPayload() { return payload; }
    public OutboxStatus getStatus() { return status; }
    public int getAttempts() { return attempts; }
    public String getLastError() { return lastError; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getPublishedAt() { return publishedAt; }
}
