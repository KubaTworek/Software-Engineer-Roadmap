package com.example.newsfeed.comment;

import com.example.newsfeed.post.Post;
import com.example.newsfeed.user.User;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "comments")
public class Comment {
    @Id private UUID id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "post_id") private Post post;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "author_id") private User author;
    @Column(nullable = false, columnDefinition = "TEXT") private String content;
    @Column(nullable = false) private Instant createdAt;
    @Column(nullable = false) private Instant updatedAt;
    private Instant deletedAt;
    protected Comment() {}
    public Comment(UUID id, Post post, User author, String content, Instant createdAt, Instant updatedAt, Instant deletedAt) { this.id = id; this.post = post; this.author = author; this.content = content; this.createdAt = createdAt; this.updatedAt = updatedAt; this.deletedAt = deletedAt; }
    public UUID getId() { return id; }
    public Post getPost() { return post; }
    public User getAuthor() { return author; }
    public String getContent() { return content; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void softDelete() { Instant now = Instant.now(); this.deletedAt = now; this.updatedAt = now; }
}
