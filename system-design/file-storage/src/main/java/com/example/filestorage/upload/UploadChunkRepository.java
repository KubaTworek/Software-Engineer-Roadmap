package com.example.filestorage.upload;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.Query;

public interface UploadChunkRepository extends JpaRepository<UploadChunk, UploadChunkId> {
    List<UploadChunk> findAllByUploadSessionIdOrderByChunkIndexAsc(UUID uploadSessionId);
    Optional<UploadChunk> findByUploadSessionIdAndChunkIndex(UUID uploadSessionId, int chunkIndex);
    long countByUploadSessionId(UUID uploadSessionId);
    @Transactional
    void deleteAllByUploadSessionId(UUID uploadSessionId);

    @Query("select distinct chunk.objectKey from UploadChunk chunk")
    List<String> findAllReferencedObjectKeys();
}
