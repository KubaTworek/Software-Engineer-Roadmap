package com.example.filestorage.sharing;

import java.time.Instant;

public record CreatePublicLinkRequest(
        PermissionRole role,
        Instant expiresAt
) {}
