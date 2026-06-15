package com.example.filestorage.sync;

import com.example.filestorage.sharing.ResourceType;
import java.time.Instant;
import java.util.UUID;

public record ChangeLogResponse(
        Long changeId,
        UUID actorUserId,
        UUID ownerId,
        ResourceType resourceType,
        UUID resourceId,
        String operation,
        String payload,
        Instant createdAt
) {
    public static ChangeLogResponse from(ChangeLog change) {
        return new ChangeLogResponse(
                change.getId(), change.getActorUserId(), change.getOwnerId(), change.getResourceType(),
                change.getResourceId(), change.getOperation(), change.getPayload(), change.getCreatedAt()
        );
    }
}
