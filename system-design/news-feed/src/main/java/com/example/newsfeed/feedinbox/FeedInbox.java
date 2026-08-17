package com.example.newsfeed.feedinbox;

import com.example.newsfeed.post.Post;
import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "feed_inbox")
public class FeedInbox {

    @EmbeddedId
    private FeedInboxId id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @MapsId("postId")
    @JoinColumn(name = "post_id", nullable = false)
    private Post post;

    @Column(nullable = false)
    private UUID authorId;

    @Column(nullable = false, length = 50)
    private String source;

    @Column(nullable = false)
    private double score;

    @Column(nullable = false)
    private Instant createdAt;

    protected FeedInbox() {
    }

    public FeedInboxId getId() { return id; }
    public Post getPost() { return post; }
    public UUID getAuthorId() { return authorId; }
    public String getSource() { return source; }
    public double getScore() { return score; }
    public Instant getCreatedAt() { return createdAt; }
}
