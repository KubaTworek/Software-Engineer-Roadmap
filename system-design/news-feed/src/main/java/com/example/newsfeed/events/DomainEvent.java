package com.example.newsfeed.events;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record DomainEvent(
        UUID eventId,
        String eventType,
        UUID actorId,
        UUID entityId,
        Instant occurredAt,
        Map<String, String> attributes
) {
    public static DomainEvent of(String eventType, UUID actorId, UUID entityId, Map<String, String> attributes) {
        return new DomainEvent(UUID.randomUUID(), eventType, actorId, entityId, Instant.now(), attributes);
    }
}
