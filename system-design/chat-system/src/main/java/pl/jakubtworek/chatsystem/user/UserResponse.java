package pl.jakubtworek.chatsystem.user;

import java.time.Instant;
import java.util.UUID;

public record UserResponse(
        UUID id,
        String username,
        String email,
        String displayName,
        Instant createdAt
) {
    public static UserResponse from(AppUser user) {
        return new UserResponse(
                user.getId(),
                user.getUsername(),
                user.getEmail(),
                user.getDisplayName(),
                user.getCreatedAt()
        );
    }
}
