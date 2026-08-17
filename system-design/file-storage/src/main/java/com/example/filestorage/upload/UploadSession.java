package com.example.filestorage.upload;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "upload_sessions")
public class UploadSession {
    @Id
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "parent_folder_id")
    private UUID parentFolderId;

    @Column(name = "filename", nullable = false)
    private String filename;

    @Column(name = "content_type", nullable = false)
    private String contentType;

    @Column(name = "total_size_bytes", nullable = false)
    private long totalSizeBytes;

    @Column(name = "chunk_size_bytes", nullable = false)
    private long chunkSizeBytes;

    @Column(name = "total_chunks", nullable = false)
    private int totalChunks;

    @Column(name = "uploaded_chunks", nullable = false)
    private int uploadedChunks;

    @Column(name = "expected_sha256")
    private String expectedSha256;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private UploadStatus status;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    protected UploadSession() {}

    public UploadSession(UUID userId, UUID parentFolderId, String filename, String contentType,
                         long totalSizeBytes, long chunkSizeBytes, int totalChunks,
                         String expectedSha256, Instant expiresAt) {
        this.id = UUID.randomUUID();
        this.userId = userId;
        this.parentFolderId = parentFolderId;
        this.filename = filename;
        this.contentType = contentType;
        this.totalSizeBytes = totalSizeBytes;
        this.chunkSizeBytes = chunkSizeBytes;
        this.totalChunks = totalChunks;
        this.uploadedChunks = 0;
        this.expectedSha256 = expectedSha256;
        this.status = UploadStatus.INITIATED;
        this.createdAt = Instant.now();
        this.updatedAt = this.createdAt;
        this.expiresAt = expiresAt;
    }

    public void markChunkUploaded() {
        this.uploadedChunks += 1;
        this.status = UploadStatus.IN_PROGRESS;
        this.updatedAt = Instant.now();
    }

    public void markCompleted() {
        this.status = UploadStatus.COMPLETED;
        this.updatedAt = Instant.now();
    }

    public void markExpired() {
        this.status = UploadStatus.EXPIRED;
        this.updatedAt = Instant.now();
    }

    public void markAborted() {
        this.status = UploadStatus.ABORTED;
        this.updatedAt = Instant.now();
    }

    public void markFailed() {
        this.status = UploadStatus.FAILED;
        this.updatedAt = Instant.now();
    }

    public boolean isWritable() {
        return (status == UploadStatus.INITIATED || status == UploadStatus.IN_PROGRESS) && expiresAt.isAfter(Instant.now());
    }

    public UUID getId() { return id; }
    public UUID getUserId() { return userId; }
    public UUID getParentFolderId() { return parentFolderId; }
    public String getFilename() { return filename; }
    public String getContentType() { return contentType; }
    public long getTotalSizeBytes() { return totalSizeBytes; }
    public long getChunkSizeBytes() { return chunkSizeBytes; }
    public int getTotalChunks() { return totalChunks; }
    public int getUploadedChunks() { return uploadedChunks; }
    public String getExpectedSha256() { return expectedSha256; }
    public UploadStatus getStatus() { return status; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public Instant getExpiresAt() { return expiresAt; }
}
