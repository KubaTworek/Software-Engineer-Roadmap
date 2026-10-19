package com.example.newsfeed.celebrity;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "celebrity_authors")
public class CelebrityAuthor {

    @Id
    private UUID authorId;

    @Column(nullable = false)
    private long followerCount;

    @Column(nullable = false)
    private Instant celebritySince;

    @Column(nullable = false)
    private Instant updatedAt;

    protected CelebrityAuthor() {
    }

    public CelebrityAuthor(UUID authorId, long followerCount, Instant celebritySince, Instant updatedAt) {
        this.authorId = authorId;
        this.followerCount = followerCount;
        this.celebritySince = celebritySince;
        this.updatedAt = updatedAt;
    }

    public UUID getAuthorId() {
        return authorId;
    }

    public long getFollowerCount() {
        return followerCount;
    }

    /** Snapshot progu jest odświeżany także wtedy, gdy autor nadal jest celebrytą. */
    public void refreshFollowerCount(long followerCount, Instant now) {
        this.followerCount = followerCount;
        this.updatedAt = now;
    }
}
