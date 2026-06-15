package com.example.filestorage.sharing;

import java.time.Instant;
import java.util.UUID;

public record PublicLinkResponse(
        UUID id,
        ResourceType resourceType,
        UUID resourceId,
        PermissionRole role,
        String url,
        UUID createdBy,
        Instant createdAt,
        Instant expiresAt,
        Instant revokedAt
) {
    public static PublicLinkResponse from(ShareLink link, String url) {
        return new PublicLinkResponse(
                link.getId(),
                link.getResourceType(),
                link.getResourceId(),
                link.getRole(),
                url,
                link.getCreatedBy(),
                link.getCreatedAt(),
                link.getExpiresAt(),
                link.getRevokedAt()
        );
    }
}
