package com.example.notification.domain;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public class NotificationJob {
    private final UUID id;
    private final UUID notificationId;
    private final String tenantId;
    private final String userId;
    private final NotificationType notificationType;
    private final Channel channel;
    private final ContactPoint contactPoint;
    private final Map<String, Object> payload;
    private NotificationStatus status;
    private int attemptCount;
    private final int maxAttempts;
    private Instant nextAttemptAt;
    private String providerName;
    private String providerMessageId;
    private String lastError;
    private boolean fallbackUsed;
    private final Instant expiresAt;
    private final Instant createdAt;
    private Instant updatedAt;

    public NotificationJob(UUID id, UUID notificationId, String tenantId, String userId,
                           NotificationType notificationType, Channel channel, ContactPoint contactPoint,
                           Map<String, Object> payload, int maxAttempts, Instant expiresAt) {
        this.id = id;
        this.notificationId = notificationId;
        this.tenantId = tenantId;
        this.userId = userId;
        this.notificationType = notificationType;
        this.channel = channel;
        this.contactPoint = contactPoint;
        this.payload = Map.copyOf(payload);
        this.status = NotificationStatus.QUEUED;
        this.maxAttempts = maxAttempts;
        this.nextAttemptAt = Instant.now();
        this.expiresAt = expiresAt;
        this.createdAt = Instant.now();
        this.updatedAt = createdAt;
    }

    public UUID getId() { return id; }
    public UUID getNotificationId() { return notificationId; }
    public String getTenantId() { return tenantId; }
    public String getUserId() { return userId; }
    public NotificationType getNotificationType() { return notificationType; }
    public Channel getChannel() { return channel; }
    public ContactPoint getContactPoint() { return contactPoint; }
    public Map<String, Object> getPayload() { return payload; }
    public NotificationStatus getStatus() { return status; }
    public int getAttemptCount() { return attemptCount; }
    public int getMaxAttempts() { return maxAttempts; }
    public Instant getNextAttemptAt() { return nextAttemptAt; }
    public String getProviderName() { return providerName; }
    public String getProviderMessageId() { return providerMessageId; }
    public String getLastError() { return lastError; }
    public boolean isFallbackUsed() { return fallbackUsed; }
    public Instant getExpiresAt() { return expiresAt; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }

    public boolean isExpired() { return expiresAt != null && Instant.now().isAfter(expiresAt); }
    public boolean canRetry() { return attemptCount < maxAttempts; }

    public void markProcessing() { transition(NotificationStatus.PROCESSING); }

    public void markSent(ProviderSendResult result, boolean fallbackUsed) {
        this.status = NotificationStatus.SENT;
        this.providerName = result.providerName();
        this.providerMessageId = result.providerMessageId();
        this.fallbackUsed = fallbackUsed;
        this.lastError = null;
        this.updatedAt = Instant.now();
    }

    public void markDelivered() { transition(NotificationStatus.DELIVERED); }
    public void markBounced(String error) { this.lastError = error; transition(NotificationStatus.BOUNCED); }
    public void markFailed(String error) { this.lastError = error; transition(NotificationStatus.FAILED); }
    public void markExpired() { this.lastError = "Notification job expired before processing"; transition(NotificationStatus.EXPIRED); }
    public void markCancelled() { transition(NotificationStatus.CANCELLED); }

    public void scheduleRetry(Instant nextAttemptAt, String error) {
        this.status = NotificationStatus.QUEUED;
        this.attemptCount++;
        this.nextAttemptAt = nextAttemptAt;
        this.lastError = error;
        this.updatedAt = Instant.now();
    }

    private void transition(NotificationStatus status) {
        this.status = status;
        this.updatedAt = Instant.now();
    }
}
