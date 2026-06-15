package com.example.filestorage.production.dedupe;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "storage_blobs")
public class StorageBlob {
    @Id
    private UUID id;
    @Column(nullable = false, unique = true, length = 64)
    private String sha256;
    @Column(name = "object_key", nullable = false, columnDefinition = "TEXT")
    private String objectKey;
    @Column(name = "size_bytes", nullable = false)
    private long sizeBytes;
    @Column(name = "ref_count", nullable = false)
    private long refCount;
    @Column(name = "encryption_key_id")
    private String encryptionKeyId;
    @Column(name = "storage_class", nullable = false)
    private String storageClass;
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected StorageBlob() {}

    public StorageBlob(String sha256, String objectKey, long sizeBytes, String encryptionKeyId) {
        this.id = UUID.randomUUID();
        this.sha256 = sha256;
        this.objectKey = objectKey;
        this.sizeBytes = sizeBytes;
        this.refCount = 1;
        this.encryptionKeyId = encryptionKeyId;
        this.storageClass = "STANDARD";
        this.createdAt = Instant.now();
        this.updatedAt = this.createdAt;
    }

    public void incrementRefCount() {
        this.refCount++;
        this.updatedAt = Instant.now();
    }

    public UUID getId() { return id; }
    public String getSha256() { return sha256; }
    public String getObjectKey() { return objectKey; }
    public long getSizeBytes() { return sizeBytes; }
    public long getRefCount() { return refCount; }
    public String getEncryptionKeyId() { return encryptionKeyId; }
    public String getStorageClass() { return storageClass; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
