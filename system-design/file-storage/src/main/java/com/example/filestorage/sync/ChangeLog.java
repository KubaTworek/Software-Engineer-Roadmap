package com.example.filestorage.sync;

import com.example.filestorage.sharing.ResourceType;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "change_log")
public class ChangeLog {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "actor_user_id", nullable = false)
    private UUID actorUserId;

    @Column(name = "owner_id", nullable = false)
    private UUID ownerId;

    @Enumerated(EnumType.STRING)
    @Column(name = "resource_type", nullable = false)
    private ResourceType resourceType;

    @Column(name = "resource_id", nullable = false)
    private UUID resourceId;

    @Column(nullable = false)
    private String operation;

    @Column(columnDefinition = "TEXT")
    private String payload;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected ChangeLog() {}

    public ChangeLog(UUID actorUserId, UUID ownerId, ResourceType resourceType, UUID resourceId, String operation, String payload) {
        this.actorUserId = actorUserId;
        this.ownerId = ownerId;
        this.resourceType = resourceType;
        this.resourceId = resourceId;
        this.operation = operation;
        this.payload = payload;
        this.createdAt = Instant.now();
    }

    public Long getId() { return id; }
    public UUID getActorUserId() { return actorUserId; }
    public UUID getOwnerId() { return ownerId; }
    public ResourceType getResourceType() { return resourceType; }
    public UUID getResourceId() { return resourceId; }
    public String getOperation() { return operation; }
    public String getPayload() { return payload; }
    public Instant getCreatedAt() { return createdAt; }
}
