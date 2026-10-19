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

    @Column(name = "final_object_key")
    private String finalObjectKey;

    @Column(name = "final_file_id")
    private UUID finalFileId;

    @Column(name = "assembling_started_at")
    private Instant assemblingStartedAt;

    @Version
    @Column(name = "row_version", nullable = false)
    private long rowVersion;

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
        if (!isWritable()) {
            throw new IllegalStateException("Upload session is no longer writable");
        }
        if (uploadedChunks >= totalChunks) {
            /*
             * Licznik nie może przekroczyć liczby części zadeklarowanej przy
             * rozpoczęciu uploadu. Ponowne zgłoszenie tego samego chunku powinno
             * zostać odfiltrowane po kluczu (sessionId, chunkIndex), a nie sztucznie
             * zwiększać postęp sesji.
             */
            throw new IllegalStateException("All declared chunks are already uploaded");
        }
        this.uploadedChunks += 1;
        this.status = UploadStatus.IN_PROGRESS;
        this.updatedAt = Instant.now();
    }

    public void markCompleted() {
        if (uploadedChunks != totalChunks) {
            /*
             * Metadane pliku nie mogą wskazywać COMPLETED, dopóki storage nie ma
             * kompletu części. Chroni to download przed opublikowaniem niepełnego
             * obiektu po retry albo równoległym wywołaniu complete.
             */
            throw new IllegalStateException("Cannot complete upload with missing chunks");
        }
        this.status = UploadStatus.COMPLETED;
        this.updatedAt = Instant.now();
    }

    public void startAssembling(String objectKey, Instant now) {
        if (status == UploadStatus.ASSEMBLING && objectKey.equals(finalObjectKey)) {
            return;
        }
        if (!isWritable(now) || uploadedChunks != totalChunks) {
            throw new IllegalStateException("Upload is not ready for assembly");
        }
        status = UploadStatus.ASSEMBLING;
        finalObjectKey = objectKey;
        assemblingStartedAt = now;
        updatedAt = now;
    }

    public void markCompleted(UUID fileId, Instant now) {
        if (status != UploadStatus.ASSEMBLING) {
            throw new IllegalStateException("Only an assembling upload can be completed");
        }
        finalFileId = fileId;
        status = UploadStatus.COMPLETED;
        updatedAt = now;
    }

    public void markFailed(Instant now) {
        status = UploadStatus.FAILED;
        updatedAt = now;
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
        return isWritable(Instant.now());
    }

    public boolean isWritable(Instant now) {
        return (status == UploadStatus.INITIATED || status == UploadStatus.IN_PROGRESS) && expiresAt.isAfter(now);
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
    public String getFinalObjectKey() { return finalObjectKey; }
    public UUID getFinalFileId() { return finalFileId; }
    public Instant getAssemblingStartedAt() { return assemblingStartedAt; }
    public long getRowVersion() { return rowVersion; }
}
