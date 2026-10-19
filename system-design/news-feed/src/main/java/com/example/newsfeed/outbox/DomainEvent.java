package com.example.newsfeed.outbox;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

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

    // columnDefinition wpływa wyłącznie na DDL. JdbcTypeCode jest potrzebny,
    // aby Hibernate wiązał parametr jako JSON zamiast VARCHAR; H2 nie ujawnia
    // tej różnicy, natomiast PostgreSQL słusznie odrzuca niejawny cast.
    @JdbcTypeCode(SqlTypes.JSON)
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
    public Instant getCreatedAt() { return createdAt; }
    public Instant getNextAttemptAt() { return nextAttemptAt; }

    /**
     * Claim ma ograniczony czas życia. Jeśli proces umrze po claimie, ale przed
     * publikacją, rekord po wygaśnięciu lease ponownie staje się kandydatem.
     */
    public void claim(Instant now, java.time.Duration lease) {
        this.status = DomainEventStatus.CLAIMED;
        this.nextAttemptAt = now.plus(lease);
    }

    public void markProcessed() {
        markProcessed(Instant.now());
    }

    public void markProcessed(Instant now) {
        this.status = DomainEventStatus.PROCESSED;
        this.processedAt = now;
        this.lastError = null;
    }

    public void markFailed(Exception exception) {
        markFailed(exception, Instant.now());
    }

    public void markFailed(Exception exception, Instant now) {
        this.status = DomainEventStatus.FAILED;
        this.attempts += 1;
        this.lastError = exception.getMessage();
        long delaySeconds = Math.min(300, (long) Math.pow(2, Math.min(this.attempts, 8)));
        this.nextAttemptAt = now.plusSeconds(delaySeconds);
    }
}
