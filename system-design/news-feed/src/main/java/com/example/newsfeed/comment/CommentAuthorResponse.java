package com.example.newsfeed.comment;
import com.example.newsfeed.user.User;
import java.util.UUID;
public record CommentAuthorResponse(UUID id, String username, String displayName) {
    public static CommentAuthorResponse from(User user) { return new CommentAuthorResponse(user.getId(), user.getUsername(), user.getDisplayName()); }
}
