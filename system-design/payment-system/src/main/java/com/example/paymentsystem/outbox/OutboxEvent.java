package com.example.paymentsystem.outbox;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "outbox_events")
public class OutboxEvent {
    /**
     * Laboratorium kończy automatyczne retry po skończonej liczbie prób. Bez
     * tego pojedynczy poison event byłby pobierany bez końca i ukrywałby
     * problem wymagający decyzji operatora.
     */
    static final int MAX_DELIVERY_ATTEMPTS = 8;
    @Id
    @Column(name = "event_id")
    private UUID eventId;
    @Column(name = "aggregate_type")
    private String aggregateType;
    @Column(name = "aggregate_id")
    private UUID aggregateId;
    @Column(name = "event_type")
    private String eventType;
    @Column(name = "payload", columnDefinition = "TEXT")
    private String payload;
    @Column(name = "created_at")
    private Instant createdAt;
    @Column(name = "published_at")
    private Instant publishedAt;
    @Column(name = "attempt_count", nullable = false)
    private int attemptCount;
    @Column(name = "next_attempt_at", nullable = false)
    private Instant nextAttemptAt;
    @Column(name = "last_error")
    private String lastError;
    @Column(name = "dead_lettered_at")
    private Instant deadLetteredAt;
    @Column(name = "event_version", nullable = false)
    private int eventVersion;
    @Column(name = "correlation_id")
    private String correlationId;

    protected OutboxEvent() {
    }

    public OutboxEvent(String aggregateType, UUID aggregateId, String eventType, String payload) {
        this.eventId = UUID.randomUUID();
        this.aggregateType = aggregateType;
        this.aggregateId = aggregateId;
        this.eventType = eventType;
        this.payload = payload;
        this.createdAt = Instant.now();
        this.nextAttemptAt = createdAt;
        this.eventVersion = 1;
        this.correlationId = aggregateId.toString();
    }

    public void published() {
        this.publishedAt = Instant.now();
        this.lastError = null;
    }

    public void failed(Throwable error) {
        attemptCount++;
        lastError = error.getClass().getSimpleName() + ": " + error.getMessage();
        if (attemptCount >= MAX_DELIVERY_ATTEMPTS) {
            deadLetteredAt = Instant.now();
            return;
        }
        long delaySeconds = Math.min(300, 1L << Math.min(attemptCount, 8));
        nextAttemptAt = Instant.now().plusSeconds(delaySeconds);
    }

    public UUID getEventId() { return eventId; }
    public String getEventType() { return eventType; }
    public String getPayload() { return payload; }
    public int getAttemptCount() { return attemptCount; }
    public Instant getPublishedAt() { return publishedAt; }
    public boolean isDeadLettered() { return deadLetteredAt != null; }
}
