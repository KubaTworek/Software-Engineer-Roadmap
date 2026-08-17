package com.example.filestorage.upload;

import java.time.Instant;
import java.util.UUID;

public record PresignedChunkUrlResponse(
        UUID uploadId,
        int chunkIndex,
        String objectKey,
        String uploadUrl,
        String method,
        Instant expiresAt
) {}
