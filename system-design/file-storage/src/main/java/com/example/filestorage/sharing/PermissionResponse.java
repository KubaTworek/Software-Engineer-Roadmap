package com.example.filestorage.sharing;

import java.time.Instant;
import java.util.UUID;

public record PermissionResponse(
        UUID id,
        ResourceType resourceType,
        UUID resourceId,
        UUID granteeUserId,
        PermissionRole role,
        UUID createdBy,
        Instant createdAt,
        Instant expiresAt,
        Instant revokedAt
) {
    public static PermissionResponse from(ResourcePermission permission) {
        return new PermissionResponse(
                permission.getId(),
                permission.getResourceType(),
                permission.getResourceId(),
                permission.getGranteeUserId(),
                permission.getRole(),
                permission.getCreatedBy(),
                permission.getCreatedAt(),
                permission.getExpiresAt(),
                permission.getRevokedAt()
        );
    }
}
