package pl.jakubtworek.chatsystem.auth;

import pl.jakubtworek.chatsystem.user.UserResponse;

public record AuthResponse(
        String accessToken,
        String tokenType,
        UserResponse user
) {}
