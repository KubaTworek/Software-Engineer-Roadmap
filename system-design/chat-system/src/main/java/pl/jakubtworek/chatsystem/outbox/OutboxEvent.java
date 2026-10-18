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

    @Column(name = "claimed_at")
    private Instant claimedAt;

    @Column(name = "claimed_by", length = 120)
    private String claimedBy;

    @Column(name = "next_attempt_at")
    private Instant nextAttemptAt;

    @Version
    private long version;

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

    public void markEnqueued(String workerId) {
        status = OutboxStatus.ENQUEUED;
        attempts++;
        claimedAt = Instant.now();
        claimedBy = workerId;
    }

    public void markPublished() {
        status = OutboxStatus.PUBLISHED;
        publishedAt = Instant.now();
        lastError = null;
        claimedAt = null;
        claimedBy = null;
    }

    public void markFailed(String error) {
        status = OutboxStatus.FAILED;
        lastError = error == null ? "unknown error" : error.substring(0, Math.min(error.length(), 2000));
        claimedAt = null;
        claimedBy = null;
        nextAttemptAt = Instant.now().plusSeconds(Math.min(60, 1L << Math.min(attempts, 5)));
    }

    public void recoverExpiredClaim() {
        status = OutboxStatus.FAILED;
        lastError = "Recovered expired outbox claim";
        claimedAt = null;
        claimedBy = null;
        nextAttemptAt = Instant.now();
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
    public Instant getClaimedAt() { return claimedAt; }
    public String getClaimedBy() { return claimedBy; }
    public Instant getNextAttemptAt() { return nextAttemptAt; }
}
