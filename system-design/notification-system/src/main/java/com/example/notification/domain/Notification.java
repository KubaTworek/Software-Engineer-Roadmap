package com.example.notification.domain;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class Notification {
    private final UUID id;
    private final String tenantId;
    private final String userId;
    private final NotificationType notificationType;
    private final List<Channel> requestedChannels;
    private final List<Channel> selectedChannels;
    private final ContactPoint contactPoint;
    private final Map<String, Object> payload;
    private final String idempotencyKey;
    private final String deduplicationKey;
    private NotificationStatus status;
    private final Instant expiresAt;
    private final Instant createdAt;
    private Instant updatedAt;
    private Instant archivedAt;

    public Notification(UUID id, String tenantId, String userId, NotificationType notificationType,
                        List<Channel> requestedChannels, List<Channel> selectedChannels, ContactPoint contactPoint,
                        Map<String, Object> payload, String idempotencyKey, String deduplicationKey, Instant expiresAt) {
        this.id = id;
        this.tenantId = tenantId;
        this.userId = userId;
        this.notificationType = notificationType;
        this.requestedChannels = List.copyOf(requestedChannels);
        this.selectedChannels = List.copyOf(selectedChannels);
        this.contactPoint = contactPoint;
        this.payload = Map.copyOf(payload);
        this.idempotencyKey = idempotencyKey;
        this.deduplicationKey = deduplicationKey;
        this.status = NotificationStatus.CREATED;
        this.expiresAt = expiresAt;
        this.createdAt = Instant.now();
        this.updatedAt = createdAt;
    }

    public UUID getId() { return id; }
    public String getTenantId() { return tenantId; }
    public String getUserId() { return userId; }
    public NotificationType getNotificationType() { return notificationType; }
    public List<Channel> getRequestedChannels() { return requestedChannels; }
    public List<Channel> getSelectedChannels() { return selectedChannels; }
    public ContactPoint getContactPoint() { return contactPoint; }
    public Map<String, Object> getPayload() { return payload; }
    public String getIdempotencyKey() { return idempotencyKey; }
    public String getDeduplicationKey() { return deduplicationKey; }
    public NotificationStatus getStatus() { return status; }
    public Instant getExpiresAt() { return expiresAt; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public Instant getArchivedAt() { return archivedAt; }

    public boolean isExpired() { return expiresAt != null && Instant.now().isAfter(expiresAt); }

    public boolean isTerminal() {
        return status == NotificationStatus.DELIVERED
                || status == NotificationStatus.BOUNCED
                || status == NotificationStatus.FAILED
                || status == NotificationStatus.EXPIRED
                || status == NotificationStatus.CANCELLED
                || status == NotificationStatus.ARCHIVED;
    }

    public void markQueued() { if (!isTerminal()) transition(NotificationStatus.QUEUED); }
    public void markProcessing() { if (!isTerminal()) transition(NotificationStatus.PROCESSING); }
    public void markSent() { if (!isTerminal()) transition(NotificationStatus.SENT); }
    public void markDelivered() { transition(NotificationStatus.DELIVERED); }
    public void markBounced() { transition(NotificationStatus.BOUNCED); }
    public void markFailed() { transition(NotificationStatus.FAILED); }
    public void markExpired() { transition(NotificationStatus.EXPIRED); }

    public void markCancelled() {
        if (status != NotificationStatus.CREATED && status != NotificationStatus.QUEUED) {
            throw new IllegalStateException("Only CREATED or QUEUED notifications can be cancelled");
        }
        transition(NotificationStatus.CANCELLED);
    }

    public void markArchived() {
        this.status = NotificationStatus.ARCHIVED;
        this.archivedAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    private void transition(NotificationStatus status) {
        this.status = status;
        this.updatedAt = Instant.now();
    }
}
