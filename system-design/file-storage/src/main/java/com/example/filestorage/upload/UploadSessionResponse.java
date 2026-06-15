package com.example.filestorage.upload;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record UploadSessionResponse(
        UUID uploadId,
        UUID parentFolderId,
        String filename,
        String contentType,
        long totalSizeBytes,
        long chunkSizeBytes,
        int totalChunks,
        int uploadedChunks,
        String expectedSha256,
        UploadStatus status,
        Instant createdAt,
        Instant updatedAt,
        Instant expiresAt,
        List<ChunkResponse> chunks
) {
    public static UploadSessionResponse from(UploadSession session, List<UploadChunk> chunks) {
        return new UploadSessionResponse(
                session.getId(),
                session.getParentFolderId(),
                session.getFilename(),
                session.getContentType(),
                session.getTotalSizeBytes(),
                session.getChunkSizeBytes(),
                session.getTotalChunks(),
                session.getUploadedChunks(),
                session.getExpectedSha256(),
                session.getStatus(),
                session.getCreatedAt(),
                session.getUpdatedAt(),
                session.getExpiresAt(),
                chunks.stream().map(ChunkResponse::from).toList()
        );
    }
}
