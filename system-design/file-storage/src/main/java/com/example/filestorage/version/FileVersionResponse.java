package com.example.filestorage.version;

import java.time.Instant;
import java.util.UUID;

public record FileVersionResponse(
        UUID id,
        UUID fileId,
        int versionNumber,
        String contentType,
        long sizeBytes,
        String sha256,
        UUID createdBy,
        Instant createdAt,
        boolean conflictVersion
) {
    public static FileVersionResponse from(FileVersion version) {
        return new FileVersionResponse(
                version.getId(),
                version.getFileId(),
                version.getVersionNumber(),
                version.getContentType(),
                version.getSizeBytes(),
                version.getSha256(),
                version.getCreatedBy(),
                version.getCreatedAt(),
                version.isConflictVersion()
        );
    }
}
