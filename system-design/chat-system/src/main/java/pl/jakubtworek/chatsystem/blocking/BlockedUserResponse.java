package pl.jakubtworek.chatsystem.blocking;

import java.time.Instant;
import java.util.UUID;

public record BlockedUserResponse(UUID userId, String username, Instant createdAt) {
    public static BlockedUserResponse from(BlockedUser blockedUser) {
        return new BlockedUserResponse(
                blockedUser.getBlocked().getId(),
                blockedUser.getBlocked().getUsername(),
                blockedUser.getCreatedAt()
        );
    }
}
