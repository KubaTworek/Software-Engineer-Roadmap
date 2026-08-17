package com.example.filestorage.folder;

import java.time.Instant;
import java.util.UUID;

public record FolderResponse(
        UUID id,
        UUID parentFolderId,
        String name,
        Instant createdAt,
        Instant updatedAt,
        Instant deletedAt
) {
    public static FolderResponse from(Folder folder) {
        return new FolderResponse(
                folder.getId(),
                folder.getParentFolderId(),
                folder.getName(),
                folder.getCreatedAt(),
                folder.getUpdatedAt(),
                folder.getDeletedAt()
        );
    }
}
