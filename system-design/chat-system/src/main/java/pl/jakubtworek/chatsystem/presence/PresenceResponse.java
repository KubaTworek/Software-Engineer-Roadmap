package pl.jakubtworek.chatsystem.presence;

import java.time.Instant;
import java.util.UUID;

public record PresenceResponse(
        UUID userId,
        PresenceStatus status,
        Instant lastSeenAt,
        Instant updatedAt
) {
    public static PresenceResponse from(UserPresence presence) {
        return new PresenceResponse(
                presence.getUser().getId(),
                presence.getStatus(),
                presence.getLastSeenAt(),
                presence.getUpdatedAt()
        );
    }
}
