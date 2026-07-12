package com.example.videostreaming.audit;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "audit_logs")
public class AuditLog {
    @Id
    private UUID id;
    @Column(name = "admin_user_id")
    private UUID adminUserId;
    @Column(name = "admin_email")
    private String adminEmail;
    @Column(nullable = false)
    private String action;
    @Column(name = "resource_type")
    private String resourceType;
    @Column(name = "resource_id")
    private String resourceId;
    @Column(name = "http_method")
    private String httpMethod;
    @Column(name = "request_path")
    private String requestPath;
    @Column(name = "status_code")
    private Integer statusCode;
    @Column(name = "ip_address")
    private String ipAddress;
    @Column(name = "user_agent")
    private String userAgent;
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected AuditLog() {}

    public AuditLog(UUID adminUserId, String adminEmail, String action, String resourceType, String resourceId,
                    String httpMethod, String requestPath, Integer statusCode, String ipAddress, String userAgent) {
        this.id = UUID.randomUUID();
        this.adminUserId = adminUserId;
        this.adminEmail = adminEmail;
        this.action = action;
        this.resourceType = resourceType;
        this.resourceId = resourceId;
        this.httpMethod = httpMethod;
        this.requestPath = requestPath;
        this.statusCode = statusCode;
        this.ipAddress = ipAddress;
        this.userAgent = userAgent;
        this.createdAt = Instant.now();
    }

    public UUID getId() { return id; }
    public UUID getAdminUserId() { return adminUserId; }
    public String getAdminEmail() { return adminEmail; }
    public String getAction() { return action; }
    public String getResourceType() { return resourceType; }
    public String getResourceId() { return resourceId; }
    public String getHttpMethod() { return httpMethod; }
    public String getRequestPath() { return requestPath; }
    public Integer getStatusCode() { return statusCode; }
    public String getIpAddress() { return ipAddress; }
    public String getUserAgent() { return userAgent; }
    public Instant getCreatedAt() { return createdAt; }
}
