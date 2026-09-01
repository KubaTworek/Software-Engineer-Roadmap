package pl.jakubtworek.cloudarchitecture.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

import java.time.Instant;

/** Durable event written in the same database transaction as its aggregate. */
@Entity
@Table(
        name = "outbox_events",
        indexes = @Index(
                name = "idx_outbox_unpublished",
                columnList = "published_at,dead_at,next_attempt_at,created_at"
        )
)
public class OutboxEventEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "aggregate_type", nullable = false)
    private String aggregateType;

    @Column(name = "aggregate_id", nullable = false)
    private Long aggregateId;

    @Column(name = "event_type", nullable = false)
    private String eventType;

    @Column(nullable = false, length = 2000)
    private String payload;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "published_at")
    private Instant publishedAt;

    @Column(name = "next_attempt_at", nullable = false)
    private Instant nextAttemptAt;

    @Column(name = "dead_at")
    private Instant deadAt;

    @Column(name = "attempts", nullable = false)
    private int attempts;

    protected OutboxEventEntity() {
        // Required by JPA.
    }

    public OutboxEventEntity(
            String aggregateType,
            Long aggregateId,
            String eventType,
            String payload,
            Instant createdAt
    ) {
        this.aggregateType = requireNonBlank(aggregateType, "aggregateType");
        this.aggregateId = requirePositive(aggregateId, "aggregateId");
        this.eventType = requireNonBlank(eventType, "eventType");
        this.payload = requireNonBlank(payload, "payload");
        this.createdAt = java.util.Objects.requireNonNull(createdAt, "createdAt must not be null");
        this.nextAttemptAt = this.createdAt;
    }

    public void markPublished(Instant timestamp) {
        if (publishedAt != null) {
            throw new IllegalStateException("outbox event is already published");
        }
        publishedAt = java.util.Objects.requireNonNull(timestamp, "timestamp must not be null");
        attempts++;
    }

    public void recordFailedAttempt(Instant timestamp, int maxAttempts) {
        if (publishedAt != null) {
            throw new IllegalStateException("published outbox event cannot fail again");
        }
        if (maxAttempts < 1) {
            throw new IllegalArgumentException("maxAttempts must be positive");
        }
        Instant failureTime = java.util.Objects.requireNonNull(timestamp, "timestamp must not be null");
        attempts++;
        if (attempts >= maxAttempts) {
            deadAt = failureTime;
            return;
        }
        long delaySeconds = Math.min(300L, 1L << Math.min(attempts - 1, 8));
        nextAttemptAt = failureTime.plusSeconds(delaySeconds);
    }

    public Long getId() { return id; }
    public Long getAggregateId() { return aggregateId; }
    public String getEventType() { return eventType; }
    public String getPayload() { return payload; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getPublishedAt() { return publishedAt; }
    public Instant getNextAttemptAt() { return nextAttemptAt; }
    public Instant getDeadAt() { return deadAt; }
    public int getAttempts() { return attempts; }

    private static String requireNonBlank(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return value;
    }

    private static Long requirePositive(Long value, String fieldName) {
        if (value == null || value <= 0) {
            throw new IllegalArgumentException(fieldName + " must be positive");
        }
        return value;
    }
}
