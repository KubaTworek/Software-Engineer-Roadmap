package com.example.notification.domain;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public class NotificationJob {
    private final UUID id;
    private final UUID notificationId;
    private final String tenantId;
    private String userId;
    private final NotificationType notificationType;
    private final Channel channel;
    private ContactPoint contactPoint;
    private Map<String, Object> payload;
    private final UUID templateId;
    private final int templateVersion;
    private NotificationStatus status;
    private int attemptCount;
    private final int maxAttempts;
    private Instant nextAttemptAt;
    private String providerName;
    private String providerMessageId;
    private String lastError;
    private boolean fallbackUsed;
    private final Instant expiresAt;
    private Instant createdAt;
    private Instant updatedAt;

    public NotificationJob(UUID id, UUID notificationId, String tenantId, String userId,
                           NotificationType notificationType, Channel channel, ContactPoint contactPoint,
                           Map<String, Object> payload, int maxAttempts, Instant expiresAt,
                           Instant scheduledAt, UUID templateId, int templateVersion) {
        this(id, notificationId, tenantId, userId, notificationType, channel, contactPoint, payload,
                maxAttempts, expiresAt, scheduledAt, templateId, templateVersion, Instant.now());
    }

    public NotificationJob(UUID id, UUID notificationId, String tenantId, String userId,
                           NotificationType notificationType, Channel channel, ContactPoint contactPoint,
                           Map<String, Object> payload, int maxAttempts, Instant expiresAt,
                           Instant scheduledAt, UUID templateId, int templateVersion, Instant now) {
        this.id = id;
        this.notificationId = notificationId;
        this.tenantId = tenantId;
        this.userId = userId;
        this.notificationType = notificationType;
        this.channel = channel;
        this.contactPoint = contactPoint;
        this.payload = Map.copyOf(payload);
        this.templateId = templateId;
        this.templateVersion = templateVersion;
        this.status = NotificationStatus.QUEUED;
        this.maxAttempts = maxAttempts;
        this.nextAttemptAt = scheduledAt != null && scheduledAt.isAfter(now) ? scheduledAt : now;
        this.expiresAt = expiresAt;
        this.createdAt = now;
        this.updatedAt = createdAt;
    }

    public static NotificationJob restore(UUID id, UUID notificationId, String tenantId, String userId,
                                          NotificationType type, Channel channel, ContactPoint contact,
                                          Map<String, Object> payload, UUID templateId, int templateVersion,
                                          NotificationStatus status, int attemptCount, int maxAttempts,
                                          Instant nextAttemptAt, String providerName, String providerMessageId,
                                          String lastError, boolean fallbackUsed, Instant expiresAt,
                                          Instant createdAt, Instant updatedAt) {
        NotificationJob job = new NotificationJob(id, notificationId, tenantId, userId, type, channel, contact,
                payload, maxAttempts, expiresAt, nextAttemptAt, templateId, templateVersion);
        job.status = status; job.attemptCount = attemptCount; job.nextAttemptAt = nextAttemptAt;
        job.providerName = providerName; job.providerMessageId = providerMessageId; job.lastError = lastError;
        job.fallbackUsed = fallbackUsed; job.createdAt = createdAt; job.updatedAt = updatedAt;
        return job;
    }

    public UUID getId() { return id; }
    public UUID getNotificationId() { return notificationId; }
    public String getTenantId() { return tenantId; }
    public String getUserId() { return userId; }
    public NotificationType getNotificationType() { return notificationType; }
    public DeliveryPriority getPriority() { return DeliveryPriority.forType(notificationType); }
    public Channel getChannel() { return channel; }
    public ContactPoint getContactPoint() { return contactPoint; }
    public Map<String, Object> getPayload() { return payload; }
    public UUID getTemplateId() { return templateId; }
    public int getTemplateVersion() { return templateVersion; }
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
    public boolean isExpired(Instant now) { return expiresAt != null && now.isAfter(expiresAt); }
    public boolean isTerminal() { return status == NotificationStatus.DELIVERED || status == NotificationStatus.BOUNCED
            || status == NotificationStatus.FAILED || status == NotificationStatus.EXPIRED
            || status == NotificationStatus.CANCELLED; }
    public boolean canRetry() { return attemptCount < maxAttempts; }

    public void markProcessing() { require(NotificationStatus.QUEUED); transition(NotificationStatus.PROCESSING); }

    public void markSent(ProviderSendResult result, boolean fallbackUsed) {
        require(NotificationStatus.PROCESSING);
        this.status = NotificationStatus.SENT;
        this.providerName = result.providerName();
        this.providerMessageId = result.providerMessageId();
        this.fallbackUsed = fallbackUsed;
        this.lastError = null;
        this.updatedAt = Instant.now();
    }

    public void markUnknown(String error) { require(NotificationStatus.PROCESSING); this.lastError = error; transition(NotificationStatus.UNKNOWN); }
    public void resolveUnknownAsSent(ProviderSendResult result) {
        require(NotificationStatus.UNKNOWN);
        this.providerName = result.providerName(); this.providerMessageId = result.providerMessageId();
        this.lastError = null; transition(NotificationStatus.SENT);
    }
    public void resolveUnknownForRetry(Instant retryAt) {
        require(NotificationStatus.UNKNOWN);
        this.attemptCount++; this.nextAttemptAt = retryAt; transition(NotificationStatus.QUEUED);
    }
    public void anonymizePersonalData() {
        this.userId = "erased:" + UUID.nameUUIDFromBytes((tenantId + ":" + userId).getBytes(java.nio.charset.StandardCharsets.UTF_8));
        this.contactPoint = new ContactPoint(null, null, null);
        this.payload = Map.of();
        this.updatedAt = Instant.now();
    }
    public void markDelivered() { if (status == NotificationStatus.DELIVERED) return; require(NotificationStatus.SENT); transition(NotificationStatus.DELIVERED); }
    public void markBounced(String error) { if (status == NotificationStatus.BOUNCED) return; require(NotificationStatus.SENT); this.lastError = error; transition(NotificationStatus.BOUNCED); }
    public void markFailed(String error) { if (status == NotificationStatus.FAILED) return; require(NotificationStatus.PROCESSING, NotificationStatus.SENT, NotificationStatus.UNKNOWN); this.lastError = error; transition(NotificationStatus.FAILED); }
    public void markExpired() { require(NotificationStatus.QUEUED); this.lastError = "Notification job expired before processing"; transition(NotificationStatus.EXPIRED); }
    public void markCancelled() { require(NotificationStatus.QUEUED); transition(NotificationStatus.CANCELLED); }

    public void scheduleRetry(Instant nextAttemptAt, String error) {
        require(NotificationStatus.PROCESSING);
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

    private void require(NotificationStatus... allowed) {
        if (java.util.Arrays.stream(allowed).noneMatch(candidate -> candidate == status)) {
            throw new IllegalStateException("Illegal job transition from " + status);
        }
    }
}
