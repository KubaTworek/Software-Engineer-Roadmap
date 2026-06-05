package com.example.filestorage.file;

import java.time.Instant;
import java.util.UUID;

public record FileResponse(
        UUID id,
        String originalFilename,
        String contentType,
        long sizeBytes,
        String sha256,
        FileStatus status,
        Instant createdAt,
        Instant updatedAt
) {
    static FileResponse from(FileMetadata file) {
        return new FileResponse(
                file.getId(),
                file.getOriginalFilename(),
                file.getContentType(),
                file.getSizeBytes(),
                file.getSha256(),
                file.getStatus(),
                file.getCreatedAt(),
                file.getUpdatedAt()
        );
    }
}
