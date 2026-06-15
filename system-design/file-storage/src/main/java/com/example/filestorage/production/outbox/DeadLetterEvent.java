package com.example.filestorage.production.outbox;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "dead_letter_events")
public class DeadLetterEvent {
    @Id
    private UUID id;
    @Column(name = "source_event_id")
    private UUID sourceEventId;
    @Column(name = "event_type", nullable = false)
    private String eventType;
    @Column(name = "aggregate_type", nullable = false)
    private String aggregateType;
    @Column(name = "aggregate_id", nullable = false)
    private UUID aggregateId;
    @Column(nullable = false, columnDefinition = "TEXT")
    private String payload;
    @Column(name = "failure_reason", nullable = false, columnDefinition = "TEXT")
    private String failureReason;
    @Column(name = "failed_at", nullable = false)
    private Instant failedAt;

    protected DeadLetterEvent() {}

    public DeadLetterEvent(OutboxEvent source, String failureReason) {
        this.id = UUID.randomUUID();
        this.sourceEventId = source.getId();
        this.eventType = source.getEventType();
        this.aggregateType = source.getAggregateType();
        this.aggregateId = source.getAggregateId();
        this.payload = source.getPayload();
        this.failureReason = failureReason;
        this.failedAt = Instant.now();
    }

    public UUID getId() { return id; }
    public UUID getSourceEventId() { return sourceEventId; }
    public String getEventType() { return eventType; }
    public String getAggregateType() { return aggregateType; }
    public UUID getAggregateId() { return aggregateId; }
    public String getPayload() { return payload; }
    public String getFailureReason() { return failureReason; }
    public Instant getFailedAt() { return failedAt; }
}
