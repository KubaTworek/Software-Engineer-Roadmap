package com.example.newsfeed.post;

import com.example.newsfeed.user.User;
import jakarta.persistence.*;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "posts")
public class Post {

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "author_id", nullable = false)
    private User author;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    @Column(columnDefinition = "TEXT")
    private String topics;

    @Column(nullable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private PostStatus status;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private PostVisibility visibility;

    private Instant deletedAt;

    protected Post() {
    }

    public Post(UUID id, User author, String content, List<String> topics, Instant createdAt, Instant updatedAt,
                Instant deletedAt, PostStatus status, PostVisibility visibility) {
        this.id = id;
        this.author = author;
        this.content = content;
        this.topics = topics == null ? "" : String.join(",", topics.stream().map(String::trim).filter(s -> !s.isBlank()).toList());
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
        this.deletedAt = deletedAt;
        this.status = status;
        this.visibility = visibility;
    }

    public UUID getId() {
        return id;
    }

    public User getAuthor() {
        return author;
    }

    public String getContent() {
        return content;
    }

    public List<String> getTopics() {
        if (topics == null || topics.isBlank()) {
            return List.of();
        }
        return List.of(topics.split(","));
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public Instant getDeletedAt() {
        return deletedAt;
    }

    public PostStatus getStatus() { return status; }
    public PostVisibility getVisibility() { return visibility; }

    public void publish() {
        publish(Instant.now());
    }

    public void publish(Instant now) { this.status = PostStatus.PUBLISHED; this.updatedAt = now; }

    public void hide() {
        hide(Instant.now());
    }

    public void hide(Instant now) { this.status = PostStatus.HIDDEN; this.updatedAt = now; }

    public void softDelete() {
        Instant now = Instant.now();
        this.deletedAt = now;
        this.status = PostStatus.DELETED;
        this.updatedAt = now;
    }
}
