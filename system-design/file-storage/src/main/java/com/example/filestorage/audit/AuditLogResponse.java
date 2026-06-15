package com.example.filestorage.audit;

import com.example.filestorage.sharing.ResourceType;
import java.time.Instant;
import java.util.UUID;

public record AuditLogResponse(
        UUID id,
        UUID actorUserId,
        String action,
        ResourceType resourceType,
        UUID resourceId,
        String message,
        Instant createdAt
) {
    public static AuditLogResponse from(AuditLog log) {
        return new AuditLogResponse(log.getId(), log.getActorUserId(), log.getAction(), log.getResourceType(), log.getResourceId(), log.getMessage(), log.getCreatedAt());
    }
}
