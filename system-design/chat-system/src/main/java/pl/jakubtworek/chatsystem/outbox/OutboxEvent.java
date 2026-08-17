package pl.jakubtworek.chatsystem.outbox;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "outbox_events", indexes = {
        @Index(name = "idx_outbox_status_created", columnList = "status, created_at"),
        @Index(name = "idx_outbox_type_created", columnList = "event_type, created_at")
})
public class OutboxEvent {
    @Id
    private UUID id = UUID.randomUUID();

    @Column(name = "aggregate_id", nullable = false)
    private UUID aggregateId;

    @Column(name = "event_type", nullable = false, length = 120)
    private String eventType;

    @Column(name = "payload_json", nullable = false, columnDefinition = "TEXT")
    private String payloadJson;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private OutboxStatus status = OutboxStatus.NEW;

    @Column(nullable = false)
    private int attempts = 0;

    @Column(name = "last_error", length = 2000)
    private String lastError;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "published_at")
    private Instant publishedAt;

    protected OutboxEvent() {}

    public OutboxEvent(UUID aggregateId, String eventType, String payloadJson) {
        this.aggregateId = aggregateId;
        this.eventType = eventType;
        this.payloadJson = payloadJson;
    }

    @PrePersist
    void onCreate() {
        createdAt = Instant.now();
    }

    public void markEnqueued() {
        status = OutboxStatus.ENQUEUED;
        attempts++;
    }

    public void markPublished() {
        status = OutboxStatus.PUBLISHED;
        publishedAt = Instant.now();
        lastError = null;
    }

    public void markFailed(String error) {
        status = OutboxStatus.FAILED;
        lastError = error == null ? "unknown error" : error.substring(0, Math.min(error.length(), 2000));
    }

    public UUID getId() { return id; }
    public UUID getAggregateId() { return aggregateId; }
    public String getEventType() { return eventType; }
    public String getPayloadJson() { return payloadJson; }
    public OutboxStatus getStatus() { return status; }
    public int getAttempts() { return attempts; }
    public String getLastError() { return lastError; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getPublishedAt() { return publishedAt; }
}
