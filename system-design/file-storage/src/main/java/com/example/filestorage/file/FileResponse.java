package com.example.filestorage.file;

import java.time.Instant;
import java.util.UUID;

public record FileResponse(
        UUID id,
        UUID parentFolderId,
        String name,
        String originalFilename,
        String contentType,
        long sizeBytes,
        String sha256,
        UUID currentVersionId,
        int currentVersionNumber,
        FileStatus status,
        Instant createdAt,
        Instant updatedAt,
        Instant deletedAt
) {
    public static FileResponse from(FileMetadata file) {
        return new FileResponse(
                file.getId(),
                file.getParentFolderId(),
                file.getName(),
                file.getOriginalFilename(),
                file.getContentType(),
                file.getSizeBytes(),
                file.getSha256(),
                file.getCurrentVersionId(),
                file.getCurrentVersionNumber(),
                file.getStatus(),
                file.getCreatedAt(),
                file.getUpdatedAt(),
                file.getDeletedAt()
        );
    }
}
