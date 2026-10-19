package com.example.notification.domain;

import java.time.Instant;
import java.util.UUID;

public class OutboxEvent {
    private final UUID id;
    private final String tenantId;
    private final UUID aggregateId;
    private final String eventType;
    private OutboxStatus status;
    private String lastError;
    private Instant createdAt;
    private Instant publishedAt;
    private Instant nextAttemptAt;
    private int attempts;

    public OutboxEvent(UUID id, String tenantId, UUID aggregateId, String eventType) {
        this.id = id;
        this.tenantId = tenantId;
        this.aggregateId = aggregateId;
        this.eventType = eventType;
        this.status = OutboxStatus.PENDING;
        this.createdAt = Instant.now();
        this.nextAttemptAt = this.createdAt;
    }
    public static OutboxEvent restore(UUID id, String tenantId, UUID aggregateId, String eventType,
                                      OutboxStatus status, String error, Instant createdAt, Instant publishedAt,
                                      Instant nextAttemptAt, int attempts) {
        OutboxEvent event = new OutboxEvent(id, tenantId, aggregateId, eventType);
        event.status = status; event.lastError = error; event.createdAt = createdAt;
        event.publishedAt = publishedAt; event.attempts = attempts;
        event.nextAttemptAt = nextAttemptAt;
        return event;
    }

    public UUID getId() { return id; }
    public String getTenantId() { return tenantId; }
    public UUID getAggregateId() { return aggregateId; }
    public String getEventType() { return eventType; }
    public OutboxStatus getStatus() { return status; }
    public String getLastError() { return lastError; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getPublishedAt() { return publishedAt; }
    public int getAttempts() { return attempts; }
    public Instant getNextAttemptAt() { return nextAttemptAt; }

    public void markPublished(Instant now) {
        this.status = OutboxStatus.PUBLISHED;
        this.publishedAt = now;
        this.lastError = null;
    }

    /**
     * Nieudana publikacja pozostaje retryowalna do jawnego limitu. Backoff
     * zapobiega gorącej pętli, a DEAD zachowuje rekord do inspekcji i replayu.
     */
    public void markFailed(String error, Instant now, int maxAttempts) {
        this.attempts++;
        this.lastError = error;
        if (attempts >= maxAttempts) {
            this.status = OutboxStatus.DEAD;
            return;
        }
        this.status = OutboxStatus.PENDING;
        long delaySeconds = Math.min(300, 1L << Math.min(attempts, 8));
        long deterministicJitter = Math.floorMod(id.hashCode(), Math.max(1, delaySeconds));
        this.nextAttemptAt = now.plusSeconds(delaySeconds + deterministicJitter);
    }
}
