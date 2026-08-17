package com.example.newsfeed.stats;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "post_stats")
public class PostStats {

    @Id
    private UUID postId;

    @Column(nullable = false)
    private long likeCount;

    @Column(nullable = false)
    private long commentCount;

    @Column(nullable = false)
    private Instant updatedAt;

    protected PostStats() {
    }

    public UUID getPostId() { return postId; }
    public long getLikeCount() { return likeCount; }
    public long getCommentCount() { return commentCount; }
    public Instant getUpdatedAt() { return updatedAt; }
}
