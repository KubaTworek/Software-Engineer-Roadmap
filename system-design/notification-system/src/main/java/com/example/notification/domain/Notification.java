package com.example.notification.domain;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class Notification {
    private final UUID id;
    private final String tenantId;
    private String userId;
    private final NotificationType notificationType;
    private final List<Channel> requestedChannels;
    private final List<Channel> selectedChannels;
    private ContactPoint contactPoint;
    private Map<String, Object> payload;
    private final String idempotencyKey;
    private final String deduplicationKey;
    private final String requestFingerprint;
    private NotificationStatus status;
    private final Instant expiresAt;
    private final Instant scheduledAt;
    private Instant createdAt;
    private Instant updatedAt;
    private Instant archivedAt;

    public Notification(UUID id, String tenantId, String userId, NotificationType notificationType,
                        List<Channel> requestedChannels, List<Channel> selectedChannels, ContactPoint contactPoint,
                        Map<String, Object> payload, String idempotencyKey, String deduplicationKey,
                        String requestFingerprint, Instant expiresAt, Instant scheduledAt) {
        this(id, tenantId, userId, notificationType, requestedChannels, selectedChannels, contactPoint, payload,
                idempotencyKey, deduplicationKey, requestFingerprint, expiresAt, scheduledAt, Instant.now());
    }

    public Notification(UUID id, String tenantId, String userId, NotificationType notificationType,
                        List<Channel> requestedChannels, List<Channel> selectedChannels, ContactPoint contactPoint,
                        Map<String, Object> payload, String idempotencyKey, String deduplicationKey,
                        String requestFingerprint, Instant expiresAt, Instant scheduledAt, Instant now) {
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
        this.requestFingerprint = requestFingerprint;
        this.status = NotificationStatus.CREATED;
        this.expiresAt = expiresAt;
        this.scheduledAt = scheduledAt;
        this.createdAt = now;
        this.updatedAt = createdAt;
    }

    public static Notification restore(UUID id, String tenantId, String userId, NotificationType type,
                                       List<Channel> requested, List<Channel> selected, ContactPoint contact,
                                       Map<String, Object> payload, String idempotencyKey, String dedupeKey,
                                       String fingerprint, NotificationStatus status, Instant expiresAt,
                                       Instant scheduledAt, Instant createdAt, Instant updatedAt, Instant archivedAt) {
        Notification n = new Notification(id, tenantId, userId, type, requested, selected, contact, payload,
                idempotencyKey, dedupeKey, fingerprint, expiresAt, scheduledAt);
        n.status = status; n.createdAt = createdAt; n.updatedAt = updatedAt; n.archivedAt = archivedAt;
        return n;
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
    public String getRequestFingerprint() { return requestFingerprint; }
    public NotificationStatus getStatus() { return status; }
    public Instant getExpiresAt() { return expiresAt; }
    public Instant getScheduledAt() { return scheduledAt; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public Instant getArchivedAt() { return archivedAt; }

    public boolean isExpired() { return expiresAt != null && Instant.now().isAfter(expiresAt); }
    public boolean isExpired(Instant now) { return expiresAt != null && now.isAfter(expiresAt); }

    public boolean isTerminal() {
        return status == NotificationStatus.DELIVERED
                || status == NotificationStatus.BOUNCED
                || status == NotificationStatus.FAILED
                || status == NotificationStatus.EXPIRED
                || status == NotificationStatus.CANCELLED
                || status == NotificationStatus.PARTIALLY_DELIVERED
                || status == NotificationStatus.ARCHIVED;
    }

    public void markQueued() { require(NotificationStatus.CREATED); transition(NotificationStatus.QUEUED); }
    public void markProcessing() { require(NotificationStatus.QUEUED, NotificationStatus.PROCESSING, NotificationStatus.SENT, NotificationStatus.UNKNOWN); transition(NotificationStatus.PROCESSING); }
    /** Reconciliation may replace an ambiguous UNKNOWN result with provider-confirmed acceptance. */
    public void markSent() { require(NotificationStatus.QUEUED, NotificationStatus.PROCESSING, NotificationStatus.SENT, NotificationStatus.UNKNOWN); transition(NotificationStatus.SENT); }
    public void markUnknown() { require(NotificationStatus.PROCESSING, NotificationStatus.SENT, NotificationStatus.UNKNOWN); transition(NotificationStatus.UNKNOWN); }
    /** A provider status lookup can prove delivery even if the original response was UNKNOWN. */
    public void markDelivered() { if (status != NotificationStatus.DELIVERED) { require(NotificationStatus.SENT, NotificationStatus.PROCESSING, NotificationStatus.UNKNOWN); transition(NotificationStatus.DELIVERED); } }
    public void markPartiallyDelivered() { if (status != NotificationStatus.PARTIALLY_DELIVERED) { require(NotificationStatus.SENT, NotificationStatus.PROCESSING, NotificationStatus.UNKNOWN); transition(NotificationStatus.PARTIALLY_DELIVERED); } }
    public void markBounced() { if (status != NotificationStatus.BOUNCED) { require(NotificationStatus.SENT, NotificationStatus.PROCESSING); transition(NotificationStatus.BOUNCED); } }
    public void markFailed() { if (!isTerminal()) transition(NotificationStatus.FAILED); }
    public void markExpired() { if (!isTerminal()) transition(NotificationStatus.EXPIRED); }

    public void markCancelled() {
        if (status != NotificationStatus.CREATED && status != NotificationStatus.QUEUED) {
            throw new IllegalStateException("Only CREATED or QUEUED notifications can be cancelled");
        }
        transition(NotificationStatus.CANCELLED);
    }

    public void markArchived() {
        if (!isTerminal()) throw new IllegalStateException("Only a terminal notification can be archived");
        this.status = NotificationStatus.ARCHIVED;
        this.archivedAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    private void transition(NotificationStatus status) {
        this.status = status;
        this.updatedAt = Instant.now();
    }

    /** Usuwa treść i contact points, zachowując techniczny ślad operacji oraz agregaty audytowe. */
    public void anonymizePersonalData() {
        this.userId = "erased:" + UUID.nameUUIDFromBytes((tenantId + ":" + userId).getBytes(java.nio.charset.StandardCharsets.UTF_8));
        this.contactPoint = new ContactPoint(null, null, null);
        this.payload = Map.of();
        this.updatedAt = Instant.now();
    }

    private void require(NotificationStatus... allowed) {
        if (java.util.Arrays.stream(allowed).noneMatch(candidate -> candidate == status)) {
            throw new IllegalStateException("Illegal notification transition from " + status);
        }
    }
}
