package com.example.newsfeed.feed;

import java.time.Instant;
import java.util.UUID;

public record FeedInboxItem(
        UUID userId,
        UUID postId,
        UUID authorId,
        double score,
        String source,
        int shardId,
        Instant createdAt
) {
}
