package com.example.newsfeed.feedinbox;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

@Embeddable
public class FeedInboxId implements Serializable {

    @Column(name = "user_id")
    private UUID userId;

    @Column(name = "post_id")
    private UUID postId;

    protected FeedInboxId() {
    }

    public FeedInboxId(UUID userId, UUID postId) {
        this.userId = userId;
        this.postId = postId;
    }

    public UUID getUserId() { return userId; }
    public UUID getPostId() { return postId; }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof FeedInboxId that)) return false;
        return Objects.equals(userId, that.userId) && Objects.equals(postId, that.postId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(userId, postId);
    }
}
