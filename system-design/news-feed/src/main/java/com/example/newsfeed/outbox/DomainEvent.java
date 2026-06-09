package com.example.newsfeed.outbox;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "domain_events")
public class DomainEvent {

    @Id
    private UUID id;

    @Column(nullable = false, length = 100)
    private String eventType;

    @Column(nullable = false)
    private UUID aggregateId;

    @Column(nullable = false, columnDefinition = "jsonb")
    private String payload;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private DomainEventStatus status;

    @Column(nullable = false)
    private int attempts;

    @Column(columnDefinition = "TEXT")
    private String lastError;

    @Column(nullable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant nextAttemptAt;

    private Instant processedAt;

    protected DomainEvent() {
    }

    public DomainEvent(UUID id, String eventType, UUID aggregateId, String payload, Instant now) {
        this.id = id;
        this.eventType = eventType;
        this.aggregateId = aggregateId;
        this.payload = payload;
        this.status = DomainEventStatus.PENDING;
        this.attempts = 0;
        this.createdAt = now;
        this.nextAttemptAt = now;
    }

    public UUID getId() { return id; }
    public String getEventType() { return eventType; }
    public UUID getAggregateId() { return aggregateId; }
    public String getPayload() { return payload; }
    public DomainEventStatus getStatus() { return status; }
    public int getAttempts() { return attempts; }

    public void markProcessed() {
        this.status = DomainEventStatus.PROCESSED;
        this.processedAt = Instant.now();
        this.lastError = null;
    }

    public void markFailed(Exception exception) {
        this.status = DomainEventStatus.FAILED;
        this.attempts += 1;
        this.lastError = exception.getMessage();
        long delaySeconds = Math.min(300, (long) Math.pow(2, Math.min(this.attempts, 8)));
        this.nextAttemptAt = Instant.now().plusSeconds(delaySeconds);
    }
}
