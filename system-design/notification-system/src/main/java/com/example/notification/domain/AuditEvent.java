package com.example.notification.domain;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record AuditEvent(
        UUID id,
        String tenantId,
        String actor,
        AuditAction action,
        UUID resourceId,
        Map<String, Object> metadata,
        Instant createdAt
) {
    public static AuditEvent of(String tenantId, String actor, AuditAction action, UUID resourceId, Map<String, Object> metadata) {
        return new AuditEvent(UUID.randomUUID(), tenantId, actor == null ? "system" : actor, action, resourceId,
                metadata == null ? Map.of() : Map.copyOf(metadata), Instant.now());
    }
}
