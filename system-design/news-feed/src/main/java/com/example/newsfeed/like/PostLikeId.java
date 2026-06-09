package com.example.newsfeed.like;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

@Embeddable
public class PostLikeId implements Serializable {
    @Column(name = "post_id") private UUID postId;
    @Column(name = "user_id") private UUID userId;
    protected PostLikeId() {}
    public PostLikeId(UUID postId, UUID userId) { this.postId = postId; this.userId = userId; }
    public UUID getPostId() { return postId; }
    public UUID getUserId() { return userId; }
    public boolean equals(Object o) { return o instanceof PostLikeId that && Objects.equals(postId, that.postId) && Objects.equals(userId, that.userId); }
    public int hashCode() { return Objects.hash(postId, userId); }
}
