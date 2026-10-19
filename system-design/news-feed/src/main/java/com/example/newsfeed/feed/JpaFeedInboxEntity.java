package com.example.newsfeed.feed;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "feed_inbox")
@IdClass(JpaFeedInboxId.class)
public class JpaFeedInboxEntity {

    @Id
    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Id
    @Column(name = "post_id", nullable = false)
    private UUID postId;

    @Column(name = "author_id", nullable = false)
    private UUID authorId;

    @Column(nullable = false)
    private double score;

    @Column(nullable = false, length = 50)
    private String source;

    @Column(name = "shard_id", nullable = false)
    private int shardId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected JpaFeedInboxEntity() {
    }

    public JpaFeedInboxEntity(UUID userId, UUID postId, UUID authorId, double score, String source, int shardId, Instant createdAt) {
        this.userId = userId;
        this.postId = postId;
        this.authorId = authorId;
        this.score = score;
        this.source = source;
        this.shardId = shardId;
        this.createdAt = createdAt;
    }

    public UUID getUserId() {
        return userId;
    }

    public UUID getPostId() {
        return postId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
