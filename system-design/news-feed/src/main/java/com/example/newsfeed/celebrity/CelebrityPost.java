package com.example.newsfeed.celebrity;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "celebrity_posts")
@IdClass(CelebrityPostId.class)
public class CelebrityPost {

    @Id
    private UUID authorId;

    @Id
    private UUID postId;

    @Column(nullable = false)
    private Instant createdAt;

    protected CelebrityPost() {
    }

    public CelebrityPost(UUID authorId, UUID postId, Instant createdAt) {
        this.authorId = authorId;
        this.postId = postId;
        this.createdAt = createdAt;
    }

    public UUID getPostId() {
        return postId;
    }
}
