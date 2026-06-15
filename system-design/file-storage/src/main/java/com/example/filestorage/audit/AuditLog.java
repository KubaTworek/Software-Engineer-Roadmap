package com.example.filestorage.audit;

import com.example.filestorage.sharing.ResourceType;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "audit_logs")
public class AuditLog {
    @Id
    private UUID id;

    @Column(name = "actor_user_id")
    private UUID actorUserId;

    @Column(nullable = false)
    private String action;

    @Enumerated(EnumType.STRING)
    @Column(name = "resource_type")
    private ResourceType resourceType;

    @Column(name = "resource_id")
    private UUID resourceId;

    @Column(nullable = false)
    private String message;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected AuditLog() {}

    public AuditLog(UUID actorUserId, String action, ResourceType resourceType, UUID resourceId, String message) {
        this.id = UUID.randomUUID();
        this.actorUserId = actorUserId;
        this.action = action;
        this.resourceType = resourceType;
        this.resourceId = resourceId;
        this.message = message;
        this.createdAt = Instant.now();
    }

    public UUID getId() { return id; }
    public UUID getActorUserId() { return actorUserId; }
    public String getAction() { return action; }
    public ResourceType getResourceType() { return resourceType; }
    public UUID getResourceId() { return resourceId; }
    public String getMessage() { return message; }
    public Instant getCreatedAt() { return createdAt; }
}
