package com.example.newsfeed.feed;

import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

public class JpaFeedInboxId implements Serializable {
    private UUID userId;
    private UUID postId;

    public JpaFeedInboxId() {
    }

    public JpaFeedInboxId(UUID userId, UUID postId) {
        this.userId = userId;
        this.postId = postId;
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof JpaFeedInboxId that)) {
            return false;
        }
        return Objects.equals(userId, that.userId) && Objects.equals(postId, that.postId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(userId, postId);
    }
}
