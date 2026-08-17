package com.example.notification.domain;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

public class NotificationTemplate {
    private final UUID id;
    private final String tenantId;
    private final String templateKey;
    private final NotificationType notificationType;
    private final Channel channel;
    private int version;
    private String subject;
    private String body;
    private Set<String> requiredVariables;
    private boolean active;
    private final Instant createdAt;
    private Instant updatedAt;

    public NotificationTemplate(UUID id, String tenantId, String templateKey, NotificationType notificationType,
                                Channel channel, int version, String subject, String body,
                                Set<String> requiredVariables, boolean active) {
        this.id = id;
        this.tenantId = tenantId;
        this.templateKey = templateKey;
        this.notificationType = notificationType;
        this.channel = channel;
        this.version = version;
        this.subject = subject;
        this.body = body;
        this.requiredVariables = Set.copyOf(requiredVariables);
        this.active = active;
        this.createdAt = Instant.now();
        this.updatedAt = createdAt;
    }

    public UUID getId() { return id; }
    public String getTenantId() { return tenantId; }
    public String getTemplateKey() { return templateKey; }
    public NotificationType getNotificationType() { return notificationType; }
    public Channel getChannel() { return channel; }
    public int getVersion() { return version; }
    public String getSubject() { return subject; }
    public String getBody() { return body; }
    public Set<String> getRequiredVariables() { return requiredVariables; }
    public boolean isActive() { return active; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }

    public void update(String subject, String body, Set<String> requiredVariables, boolean active) {
        this.version++;
        this.subject = subject;
        this.body = body;
        this.requiredVariables = Set.copyOf(requiredVariables);
        this.active = active;
        this.updatedAt = Instant.now();
    }

    public void deactivate() {
        this.active = false;
        this.updatedAt = Instant.now();
    }
}
