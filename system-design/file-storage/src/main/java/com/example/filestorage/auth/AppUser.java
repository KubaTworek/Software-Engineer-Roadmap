package com.example.filestorage.auth;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "app_users")
public class AppUser {
    public static final long DEFAULT_STORAGE_QUOTA_BYTES = 1024L * 1024L * 1024L; // 1 GiB

    @Id
    private UUID id;

    @Column(nullable = false, unique = true)
    private String email;

    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    @Column(name = "display_name", nullable = false)
    private String displayName;

    @Column(name = "storage_quota_bytes", nullable = false)
    private long storageQuotaBytes;

    @Column(name = "storage_used_bytes", nullable = false)
    private long storageUsedBytes;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected AppUser() {}

    public AppUser(String email, String passwordHash, String displayName) {
        this.id = UUID.randomUUID();
        this.email = email.toLowerCase();
        this.passwordHash = passwordHash;
        this.displayName = displayName;
        this.storageQuotaBytes = DEFAULT_STORAGE_QUOTA_BYTES;
        this.storageUsedBytes = 0L;
        this.createdAt = Instant.now();
        this.updatedAt = this.createdAt;
    }

    public void reserveStorage(long bytes) {
        if (bytes < 0) {
            throw new IllegalArgumentException("Storage delta must not be negative");
        }
        if (this.storageUsedBytes + bytes > this.storageQuotaBytes) {
            throw new IllegalArgumentException("Storage quota exceeded");
        }
        this.storageUsedBytes += bytes;
        this.updatedAt = Instant.now();
    }

    public void releaseStorage(long bytes) {
        if (bytes < 0) {
            throw new IllegalArgumentException("Storage delta must not be negative");
        }
        this.storageUsedBytes = Math.max(0, this.storageUsedBytes - bytes);
        this.updatedAt = Instant.now();
    }

    public UUID getId() { return id; }
    public String getEmail() { return email; }
    public String getPasswordHash() { return passwordHash; }
    public String getDisplayName() { return displayName; }
    public long getStorageQuotaBytes() { return storageQuotaBytes; }
    public long getStorageUsedBytes() { return storageUsedBytes; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
