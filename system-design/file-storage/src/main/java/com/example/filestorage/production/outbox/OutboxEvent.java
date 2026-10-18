package com.example.filestorage.production.outbox;

import jakarta.persistence.*;
import java.time.Instant;
import java.time.Duration;
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
    @Column(name = "claimed_at")
    private Instant claimedAt;
    @Column(name = "next_attempt_at", nullable = false)
    private Instant nextAttemptAt;

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
        this.nextAttemptAt = this.createdAt;
    }

    public void claim(Instant now) {
        status = OutboxStatus.PROCESSING;
        attempts++;
        claimedAt = now;
    }

    public void markPublished() {
        this.status = OutboxStatus.PUBLISHED;
        this.publishedAt = Instant.now();
        this.lastError = null;
        this.claimedAt = null;
    }

    public void markFailed(String error) {
        this.status = OutboxStatus.FAILED;
        this.lastError = error;
        this.claimedAt = null;
    }

    public void retryLater(String error) {
        this.status = OutboxStatus.PENDING;
        this.lastError = error;
        this.claimedAt = null;
        this.nextAttemptAt = Instant.now().plus(Duration.ofSeconds(Math.min(300, 1L << Math.min(attempts, 8))));
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
    public Instant getClaimedAt() { return claimedAt; }
    public Instant getNextAttemptAt() { return nextAttemptAt; }
}
