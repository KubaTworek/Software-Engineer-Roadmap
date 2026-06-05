package com.example.newsfeed.post;

import java.time.Instant;
import java.util.UUID;

public record PostResponse(
        UUID id,
        PostAuthorResponse author,
        String content,
        Instant createdAt,
        Instant updatedAt
) {
    public static PostResponse from(Post post) {
        return new PostResponse(
                post.getId(),
                PostAuthorResponse.from(post.getAuthor()),
                post.getContent(),
                post.getCreatedAt(),
                post.getUpdatedAt()
        );
    }
}
