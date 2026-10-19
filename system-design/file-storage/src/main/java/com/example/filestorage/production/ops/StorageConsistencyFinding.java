package com.example.filestorage.production.ops;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/** Trwaly slad wykrytej rozbieznosci; rekoncyliacja nie naprawia danych w ciemno. */
@Entity
@Table(name = "storage_consistency_findings")
public class StorageConsistencyFinding {
    @Id private UUID id;
    @Column(name = "finding_type", nullable = false) private String findingType;
    @Column(name = "object_key", nullable = false, columnDefinition = "TEXT") private String objectKey;
    @Column(name = "file_id") private UUID fileId;
    @Column(nullable = false, columnDefinition = "TEXT") private String details;
    @Column(name = "detected_at", nullable = false) private Instant detectedAt;
    @Column(name = "resolved_at") private Instant resolvedAt;

    protected StorageConsistencyFinding() {}

    public StorageConsistencyFinding(String findingType, String objectKey, UUID fileId, String details, Instant now) {
        this.id = UUID.randomUUID();
        this.findingType = findingType;
        this.objectKey = objectKey;
        this.fileId = fileId;
        this.details = details;
        this.detectedAt = now;
    }

    public UUID getId() { return id; }
    public String getFindingType() { return findingType; }
    public String getObjectKey() { return objectKey; }
    public UUID getFileId() { return fileId; }
    public String getDetails() { return details; }
    public Instant getDetectedAt() { return detectedAt; }
    public Instant getResolvedAt() { return resolvedAt; }
}
