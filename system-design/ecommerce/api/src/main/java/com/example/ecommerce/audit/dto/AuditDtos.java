package com.example.ecommerce.audit.dto;
import java.time.Instant;
public final class AuditDtos { private AuditDtos() {} public record AdminAuditLogResponse(Long id, Long adminUserId, String adminEmail, String action, String entityType, String entityId, Instant createdAt) {} }
