package com.example.filestorage.upload;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UploadChunkRepository extends JpaRepository<UploadChunk, UploadChunkId> {
    List<UploadChunk> findAllByUploadSessionIdOrderByChunkIndexAsc(UUID uploadSessionId);
    Optional<UploadChunk> findByUploadSessionIdAndChunkIndex(UUID uploadSessionId, int chunkIndex);
    long countByUploadSessionId(UUID uploadSessionId);
    void deleteAllByUploadSessionId(UUID uploadSessionId);
}
