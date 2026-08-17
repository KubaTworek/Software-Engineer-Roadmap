package com.example.filestorage.production.ops;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "storage_cost_snapshots")
public class StorageCostSnapshot {
    @Id
    private UUID id;
    @Column(name = "total_objects", nullable = false)
    private long totalObjects;
    @Column(name = "total_logical_bytes", nullable = false)
    private long totalLogicalBytes;
    @Column(name = "total_blob_bytes", nullable = false)
    private long totalBlobBytes;
    @Column(name = "estimated_monthly_cost_usd", nullable = false)
    private BigDecimal estimatedMonthlyCostUsd;
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected StorageCostSnapshot() {}

    public StorageCostSnapshot(long totalObjects, long totalLogicalBytes, long totalBlobBytes, BigDecimal estimatedMonthlyCostUsd) {
        this.id = UUID.randomUUID();
        this.totalObjects = totalObjects;
        this.totalLogicalBytes = totalLogicalBytes;
        this.totalBlobBytes = totalBlobBytes;
        this.estimatedMonthlyCostUsd = estimatedMonthlyCostUsd;
        this.createdAt = Instant.now();
    }

    public UUID getId() { return id; }
    public long getTotalObjects() { return totalObjects; }
    public long getTotalLogicalBytes() { return totalLogicalBytes; }
    public long getTotalBlobBytes() { return totalBlobBytes; }
    public BigDecimal getEstimatedMonthlyCostUsd() { return estimatedMonthlyCostUsd; }
    public Instant getCreatedAt() { return createdAt; }
}
