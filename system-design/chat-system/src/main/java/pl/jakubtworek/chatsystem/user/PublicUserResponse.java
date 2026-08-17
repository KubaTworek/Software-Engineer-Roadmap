package pl.jakubtworek.chatsystem.user;

import java.util.UUID;

public record PublicUserResponse(
        UUID id,
        String username,
        String displayName
) {
    public static PublicUserResponse from(AppUser user) {
        return new PublicUserResponse(user.getId(), user.getUsername(), user.getDisplayName());
    }
}
