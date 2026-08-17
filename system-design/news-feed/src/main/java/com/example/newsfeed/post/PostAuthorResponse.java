package com.example.newsfeed.post;

import com.example.newsfeed.user.User;

import java.util.UUID;

public record PostAuthorResponse(
        UUID id,
        String username,
        String displayName
) {
    public static PostAuthorResponse from(User user) {
        return new PostAuthorResponse(user.getId(), user.getUsername(), user.getDisplayName());
    }
}
