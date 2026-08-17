package com.example.filestorage.version;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "file_versions")
public class FileVersion {
    @Id
    private UUID id;

    @Column(name = "file_id", nullable = false)
    private UUID fileId;

    @Column(name = "version_number", nullable = false)
    private int versionNumber;

    @Column(name = "object_key", nullable = false, unique = true)
    private String objectKey;

    @Column(name = "content_type", nullable = false)
    private String contentType;

    @Column(name = "size_bytes", nullable = false)
    private long sizeBytes;

    @Column(nullable = false)
    private String sha256;

    @Column(name = "created_by", nullable = false)
    private UUID createdBy;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "conflict_version", nullable = false)
    private boolean conflictVersion;

    protected FileVersion() {}

    public FileVersion(UUID fileId, int versionNumber, String objectKey, String contentType, long sizeBytes, String sha256, UUID createdBy, boolean conflictVersion) {
        this.id = UUID.randomUUID();
        this.fileId = fileId;
        this.versionNumber = versionNumber;
        this.objectKey = objectKey;
        this.contentType = contentType;
        this.sizeBytes = sizeBytes;
        this.sha256 = sha256;
        this.createdBy = createdBy;
        this.createdAt = Instant.now();
        this.conflictVersion = conflictVersion;
    }

    public UUID getId() { return id; }
    public UUID getFileId() { return fileId; }
    public int getVersionNumber() { return versionNumber; }
    public String getObjectKey() { return objectKey; }
    public String getContentType() { return contentType; }
    public long getSizeBytes() { return sizeBytes; }
    public String getSha256() { return sha256; }
    public UUID getCreatedBy() { return createdBy; }
    public Instant getCreatedAt() { return createdAt; }
    public boolean isConflictVersion() { return conflictVersion; }
}
