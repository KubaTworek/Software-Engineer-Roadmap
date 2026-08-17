package com.example.paymentsystem.audit;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "audit_logs")
public class AuditLog {
    @Id
    @Column(name = "audit_id")
    private UUID auditId;
    @Column(name = "actor")
    private String actor;
    @Column(name = "action")
    private String action;
    @Column(name = "target_type")
    private String targetType;
    @Column(name = "target_id")
    private String targetId;
    @Lob
    @Column(name = "details")
    private String details;
    @Column(name = "created_at")
    private Instant createdAt;

    protected AuditLog() {
    }

    public AuditLog(String actor, String action, String targetType, String targetId, String details) {
        this.auditId = UUID.randomUUID();
        this.actor = actor;
        this.action = action;
        this.targetType = targetType;
        this.targetId = targetId;
        this.details = details;
        this.createdAt = Instant.now();
    }
}
