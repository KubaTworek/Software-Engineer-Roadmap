package com.example.newsfeed.feed;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "feed_inbox")
@IdClass(JpaFeedInboxId.class)
public class JpaFeedInboxEntity {

    @Id
    private UUID userId;

    @Id
    private UUID postId;

    @Column(nullable = false)
    private UUID authorId;

    @Column(nullable = false)
    private double score;

    @Column(nullable = false, length = 50)
    private String source;

    @Column(nullable = false)
    private int shardId;

    @Column(nullable = false)
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
