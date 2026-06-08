package com.example.notification.domain;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class Campaign {
    private final UUID id;
    private final String tenantId;
    private final String name;
    private final NotificationType notificationType;
    private final List<String> userIds;
    private final List<Channel> channels;
    private final Map<String, Object> payload;
    private CampaignStatus status;
    private final Instant createdAt;
    private Instant updatedAt;

    public Campaign(UUID id, String tenantId, String name, NotificationType notificationType,
                    List<String> userIds, List<Channel> channels, Map<String, Object> payload) {
        this.id = id;
        this.tenantId = tenantId;
        this.name = name;
        this.notificationType = notificationType;
        this.userIds = List.copyOf(userIds);
        this.channels = List.copyOf(channels);
        this.payload = Map.copyOf(payload);
        this.status = CampaignStatus.CREATED;
        this.createdAt = Instant.now();
        this.updatedAt = createdAt;
    }

    public UUID getId() { return id; }
    public String getTenantId() { return tenantId; }
    public String getName() { return name; }
    public NotificationType getNotificationType() { return notificationType; }
    public List<String> getUserIds() { return userIds; }
    public List<Channel> getChannels() { return channels; }
    public Map<String, Object> getPayload() { return payload; }
    public CampaignStatus getStatus() { return status; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }

    public void markRunning() { this.status = CampaignStatus.RUNNING; this.updatedAt = Instant.now(); }
    public void markCompleted() { this.status = CampaignStatus.COMPLETED; this.updatedAt = Instant.now(); }
    public void markCancelled() { this.status = CampaignStatus.CANCELLED; this.updatedAt = Instant.now(); }
}
