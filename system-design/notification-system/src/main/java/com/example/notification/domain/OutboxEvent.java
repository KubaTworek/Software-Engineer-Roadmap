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
    private final Instant createdAt;
    private Instant publishedAt;

    public OutboxEvent(UUID id, String tenantId, UUID aggregateId, String eventType) {
        this.id = id;
        this.tenantId = tenantId;
        this.aggregateId = aggregateId;
        this.eventType = eventType;
        this.status = OutboxStatus.PENDING;
        this.createdAt = Instant.now();
    }

    public UUID getId() { return id; }
    public String getTenantId() { return tenantId; }
    public UUID getAggregateId() { return aggregateId; }
    public String getEventType() { return eventType; }
    public OutboxStatus getStatus() { return status; }
    public String getLastError() { return lastError; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getPublishedAt() { return publishedAt; }

    public void markPublished() {
        this.status = OutboxStatus.PUBLISHED;
        this.publishedAt = Instant.now();
        this.lastError = null;
    }

    public void markFailed(String error) {
        this.status = OutboxStatus.FAILED;
        this.lastError = error;
    }
}
