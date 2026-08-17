package com.example.filestorage.file;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "file_metadata")
public class FileMetadata {
    @Id
    private UUID id;

    @Column(name = "owner_id", nullable = false)
    private UUID ownerId;

    @Column(name = "parent_folder_id")
    private UUID parentFolderId;

    @Column(nullable = false)
    private String name;

    @Column(name = "original_filename", nullable = false)
    private String originalFilename;

    @Column(name = "content_type", nullable = false)
    private String contentType;

    @Column(name = "size_bytes", nullable = false)
    private long sizeBytes;

    @Column(name = "object_key", nullable = false, unique = true)
    private String objectKey;

    @Column(nullable = false)
    private String sha256;

    @Column(name = "current_version_id")
    private UUID currentVersionId;

    @Column(name = "current_version_number", nullable = false)
    private int currentVersionNumber;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private FileStatus status;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    protected FileMetadata() {}

    public FileMetadata(UUID ownerId, UUID parentFolderId, String name, String originalFilename, String contentType, long sizeBytes, String objectKey, String sha256) {
        this.id = UUID.randomUUID();
        this.ownerId = ownerId;
        this.parentFolderId = parentFolderId;
        this.name = name;
        this.originalFilename = originalFilename;
        this.contentType = contentType;
        this.sizeBytes = sizeBytes;
        this.objectKey = objectKey;
        this.sha256 = sha256;
        this.currentVersionNumber = 1;
        this.status = FileStatus.ACTIVE;
        this.createdAt = Instant.now();
        this.updatedAt = this.createdAt;
    }

    public void rename(String newName) {
        this.name = newName;
        this.updatedAt = Instant.now();
    }

    public void moveTo(UUID newParentFolderId) {
        this.parentFolderId = newParentFolderId;
        this.updatedAt = Instant.now();
    }

    public void replaceContent(UUID versionId, int versionNumber, String contentType, long sizeBytes, String objectKey, String sha256) {
        this.currentVersionId = versionId;
        this.currentVersionNumber = versionNumber;
        this.contentType = contentType;
        this.sizeBytes = sizeBytes;
        this.objectKey = objectKey;
        this.sha256 = sha256;
        this.updatedAt = Instant.now();
    }

    public void setInitialVersion(UUID versionId) {
        this.currentVersionId = versionId;
        this.currentVersionNumber = 1;
        this.updatedAt = Instant.now();
    }

    public void restoreVersion(UUID versionId, int versionNumber, String contentType, long sizeBytes, String objectKey, String sha256) {
        replaceContent(versionId, versionNumber, contentType, sizeBytes, objectKey, sha256);
    }

    public void softDelete() {
        this.status = FileStatus.DELETED;
        this.deletedAt = Instant.now();
        this.updatedAt = this.deletedAt;
    }

    public void restore(UUID parentFolderId) {
        this.status = FileStatus.ACTIVE;
        this.parentFolderId = parentFolderId;
        this.deletedAt = null;
        this.updatedAt = Instant.now();
    }

    public UUID getId() { return id; }
    public UUID getOwnerId() { return ownerId; }
    public UUID getParentFolderId() { return parentFolderId; }
    public String getName() { return name; }
    public String getOriginalFilename() { return originalFilename; }
    public String getContentType() { return contentType; }
    public long getSizeBytes() { return sizeBytes; }
    public String getObjectKey() { return objectKey; }
    public String getSha256() { return sha256; }
    public UUID getCurrentVersionId() { return currentVersionId; }
    public int getCurrentVersionNumber() { return currentVersionNumber; }
    public FileStatus getStatus() { return status; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public Instant getDeletedAt() { return deletedAt; }
}
