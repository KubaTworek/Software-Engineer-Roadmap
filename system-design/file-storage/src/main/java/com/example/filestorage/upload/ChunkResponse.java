package com.example.filestorage.upload;

import java.time.Instant;

public record ChunkResponse(
        int chunkIndex,
        String sha256,
        long sizeBytes,
        Instant uploadedAt
) {
    public static ChunkResponse from(UploadChunk chunk) {
        return new ChunkResponse(chunk.getChunkIndex(), chunk.getSha256(), chunk.getSizeBytes(), chunk.getUploadedAt());
    }
}
