package com.example.filestorage.sharing;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "resource_permissions")
public class ResourcePermission {
    @Id
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(name = "resource_type", nullable = false)
    private ResourceType resourceType;

    @Column(name = "resource_id", nullable = false)
    private UUID resourceId;

    @Column(name = "grantee_user_id", nullable = false)
    private UUID granteeUserId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PermissionRole role;

    @Column(name = "created_by", nullable = false)
    private UUID createdBy;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "expires_at")
    private Instant expiresAt;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    protected ResourcePermission() {}

    public ResourcePermission(ResourceType resourceType, UUID resourceId, UUID granteeUserId, PermissionRole role, UUID createdBy, Instant expiresAt) {
        this.id = UUID.randomUUID();
        this.resourceType = resourceType;
        this.resourceId = resourceId;
        this.granteeUserId = granteeUserId;
        this.role = role;
        this.createdBy = createdBy;
        this.createdAt = Instant.now();
        this.expiresAt = expiresAt;
    }

    public void updateRole(PermissionRole role, Instant expiresAt) {
        this.role = role;
        this.expiresAt = expiresAt;
        this.revokedAt = null;
    }

    public void revoke() {
        this.revokedAt = Instant.now();
    }

    public boolean isActive() {
        return revokedAt == null && (expiresAt == null || expiresAt.isAfter(Instant.now()));
    }

    public UUID getId() { return id; }
    public ResourceType getResourceType() { return resourceType; }
    public UUID getResourceId() { return resourceId; }
    public UUID getGranteeUserId() { return granteeUserId; }
    public PermissionRole getRole() { return role; }
    public UUID getCreatedBy() { return createdBy; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getExpiresAt() { return expiresAt; }
    public Instant getRevokedAt() { return revokedAt; }
}
