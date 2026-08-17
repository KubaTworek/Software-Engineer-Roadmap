package com.example.ecommerce.audit;
import jakarta.persistence.*; import java.time.Instant;
@Entity
@Table(name = "admin_audit_logs", indexes = { @Index(name = "idx_admin_audit_created", columnList = "createdAt"), @Index(name = "idx_admin_audit_entity", columnList = "entityType,entityId") })
public class AdminAuditLog {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    private Long adminUserId; @Column(nullable = false) private String adminEmail; @Column(nullable = false) private String action; @Column(nullable = false) private String entityType; private String entityId;
    @Lob private String oldValueJson; @Lob private String newValueJson; private String ipAddress; @Column(nullable = false) private Instant createdAt = Instant.now();
    protected AdminAuditLog() {}
    public AdminAuditLog(Long adminUserId, String adminEmail, String action, String entityType, String entityId, String oldValueJson, String newValueJson, String ipAddress){ this.adminUserId=adminUserId; this.adminEmail=adminEmail; this.action=action; this.entityType=entityType; this.entityId=entityId; this.oldValueJson=oldValueJson; this.newValueJson=newValueJson; this.ipAddress=ipAddress; }
    public Long getId(){return id;} public Long getAdminUserId(){return adminUserId;} public String getAdminEmail(){return adminEmail;} public String getAction(){return action;} public String getEntityType(){return entityType;} public String getEntityId(){return entityId;} public Instant getCreatedAt(){return createdAt;}
}
