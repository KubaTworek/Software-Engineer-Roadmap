package com.example.newsfeed.post;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record PostResponse(
        UUID id,
        PostAuthorResponse author,
        String content,
        List<String> topics,
        PostStatsResponse stats,
        PostViewerStateResponse viewerState,
        Instant createdAt,
        Instant updatedAt
) {
    public static PostResponse from(Post post) {
        return from(post, 0, 0, false);
    }

    public static PostResponse from(Post post, long likeCount, long commentCount, boolean likedByMe) {
        return new PostResponse(
                post.getId(),
                PostAuthorResponse.from(post.getAuthor()),
                post.getContent(),
                post.getTopics(),
                new PostStatsResponse(likeCount, commentCount),
                new PostViewerStateResponse(likedByMe),
                post.getCreatedAt(),
                post.getUpdatedAt()
        );
    }
}
