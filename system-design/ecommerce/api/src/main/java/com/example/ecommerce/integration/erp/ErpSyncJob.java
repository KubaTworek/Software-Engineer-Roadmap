package com.example.ecommerce.integration.erp;

import jakarta.persistence.*;

import java.time.Instant;

@Entity
@Table(name = "erp_sync_jobs")
public class ErpSyncJob {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String entityType;
    private String entityId;
    private String operation;

    @Lob
    private String payloadJson;

    @Enumerated(EnumType.STRING)
    private ErpSyncStatus status = ErpSyncStatus.NEW;

    private int attempts = 0;
    private String lastError;
    private Instant createdAt = Instant.now();
    private Instant syncedAt;

    protected ErpSyncJob() {}

    public ErpSyncJob(String entityType, String entityId, String operation, String payloadJson) {
        this.entityType = entityType;
        this.entityId = entityId;
        this.operation = operation;
        this.payloadJson = payloadJson;
    }

    public Long getId() { return id; }
    public String getEntityType() { return entityType; }
    public String getEntityId() { return entityId; }
    public String getOperation() { return operation; }
    public String getPayloadJson() { return payloadJson; }
    public ErpSyncStatus getStatus() { return status; }

    public void markSent() { this.status = ErpSyncStatus.SENT; this.syncedAt = Instant.now(); }
    public void markFailed(String error) { this.status = ErpSyncStatus.FAILED; this.lastError = error; this.attempts++; }
}
