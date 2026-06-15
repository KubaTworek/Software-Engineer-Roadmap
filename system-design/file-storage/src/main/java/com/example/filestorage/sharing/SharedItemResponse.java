package com.example.filestorage.sharing;

import java.time.Instant;
import java.util.UUID;

public record SharedItemResponse(
        UUID permissionId,
        ResourceType resourceType,
        UUID resourceId,
        String name,
        PermissionRole role,
        Instant expiresAt
) {}
