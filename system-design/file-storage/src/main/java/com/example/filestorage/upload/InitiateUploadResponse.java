package com.example.filestorage.upload;

import java.time.Instant;
import java.util.UUID;

public record InitiateUploadResponse(
        UUID uploadId,
        String filename,
        long totalSizeBytes,
        long chunkSizeBytes,
        int totalChunks,
        Instant expiresAt
) {}
