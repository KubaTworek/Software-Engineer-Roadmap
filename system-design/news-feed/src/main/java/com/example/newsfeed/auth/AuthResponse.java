package com.example.newsfeed.auth;

import com.example.newsfeed.user.UserResponse;

public record AuthResponse(
        String token,
        UserResponse user
) {
}
