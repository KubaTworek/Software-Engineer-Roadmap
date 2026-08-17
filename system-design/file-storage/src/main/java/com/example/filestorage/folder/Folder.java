package com.example.filestorage.folder;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "folders")
public class Folder {
    @Id
    private UUID id;

    @Column(name = "owner_id", nullable = false)
    private UUID ownerId;

    @Column(name = "parent_folder_id")
    private UUID parentFolderId;

    @Column(nullable = false)
    private String name;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    protected Folder() {}

    public Folder(UUID ownerId, UUID parentFolderId, String name) {
        this.id = UUID.randomUUID();
        this.ownerId = ownerId;
        this.parentFolderId = parentFolderId;
        this.name = name;
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

    public void softDelete() {
        this.deletedAt = Instant.now();
        this.updatedAt = this.deletedAt;
    }

    public void restore(UUID parentFolderId) {
        this.parentFolderId = parentFolderId;
        this.deletedAt = null;
        this.updatedAt = Instant.now();
    }

    public UUID getId() { return id; }
    public UUID getOwnerId() { return ownerId; }
    public UUID getParentFolderId() { return parentFolderId; }
    public String getName() { return name; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public Instant getDeletedAt() { return deletedAt; }
}
