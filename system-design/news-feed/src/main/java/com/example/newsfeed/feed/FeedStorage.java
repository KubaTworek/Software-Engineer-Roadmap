package com.example.newsfeed.feed;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface FeedStorage {
    void appendIdempotent(Collection<FeedInboxItem> items);

    void removePost(UUID postId);

    List<UUID> getPostIds(UUID userId, Instant beforeCreatedAt, UUID beforePostId, int limit);
}
