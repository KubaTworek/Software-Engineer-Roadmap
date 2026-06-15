package com.example.filestorage.upload;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "upload_chunks")
@IdClass(UploadChunkId.class)
public class UploadChunk {
    @Id
    @Column(name = "upload_session_id", nullable = false)
    private UUID uploadSessionId;

    @Id
    @Column(name = "chunk_index", nullable = false)
    private int chunkIndex;

    @Column(name = "object_key", nullable = false)
    private String objectKey;

    @Column(nullable = false)
    private String sha256;

    @Column(name = "size_bytes", nullable = false)
    private long sizeBytes;

    @Column(name = "uploaded_at", nullable = false)
    private Instant uploadedAt;

    protected UploadChunk() {}

    public UploadChunk(UUID uploadSessionId, int chunkIndex, String objectKey, String sha256, long sizeBytes) {
        this.uploadSessionId = uploadSessionId;
        this.chunkIndex = chunkIndex;
        this.objectKey = objectKey;
        this.sha256 = sha256;
        this.sizeBytes = sizeBytes;
        this.uploadedAt = Instant.now();
    }

    public UUID getUploadSessionId() { return uploadSessionId; }
    public int getChunkIndex() { return chunkIndex; }
    public String getObjectKey() { return objectKey; }
    public String getSha256() { return sha256; }
    public long getSizeBytes() { return sizeBytes; }
    public Instant getUploadedAt() { return uploadedAt; }
}
