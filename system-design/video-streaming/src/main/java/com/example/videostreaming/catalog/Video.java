package com.example.videostreaming.catalog;

import com.example.videostreaming.auth.User;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "videos")
public class Video {
    @Id
    private UUID id;
    @Column(nullable = false)
    private String title;
    private String description;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private VideoStatus status;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private VideoVisibility visibility;
    @Column(name = "duration_seconds")
    private Integer durationSeconds;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "owner_id")
    private User owner;
    @Column(name = "source_object_key")
    private String sourceObjectKey;
    @Column(name = "hls_master_object_key")
    private String hlsMasterObjectKey;
    @Column(name = "thumbnail_object_key")
    private String thumbnailObjectKey;
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
    @Column(name = "published_at")
    private Instant publishedAt;

    protected Video() {}

    public Video(String title, String description, User owner) {
        this.id = UUID.randomUUID();
        this.title = title;
        this.description = description;
        this.owner = owner;
        this.status = VideoStatus.DRAFT;
        this.visibility = VideoVisibility.PRIVATE;
        this.createdAt = Instant.now();
        this.updatedAt = this.createdAt;
    }

    public UUID getId() { return id; }
    public String getTitle() { return title; }
    public String getDescription() { return description; }
    public VideoStatus getStatus() { return status; }
    public VideoVisibility getVisibility() { return visibility; }
    public Integer getDurationSeconds() { return durationSeconds; }
    public User getOwner() { return owner; }
    public String getSourceObjectKey() { return sourceObjectKey; }
    public String getHlsMasterObjectKey() { return hlsMasterObjectKey; }
    public String getThumbnailObjectKey() { return thumbnailObjectKey; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public Instant getPublishedAt() { return publishedAt; }

    public void updateMetadata(String title, String description) {
        this.title = title;
        this.description = description;
        touch();
    }
    public void markUploading() { this.status = VideoStatus.UPLOADING; touch(); }
    public void markUploaded(String sourceObjectKey) { this.sourceObjectKey = sourceObjectKey; this.status = VideoStatus.UPLOADED; touch(); }
    public void markProcessing() { this.status = VideoStatus.PROCESSING; touch(); }
    public void markReady(String hlsMasterObjectKey) { this.hlsMasterObjectKey = hlsMasterObjectKey; this.status = VideoStatus.READY; touch(); }
    public void markFailed() { this.status = VideoStatus.FAILED; touch(); }
    public void publish() { this.status = VideoStatus.PUBLISHED; this.visibility = VideoVisibility.PUBLIC; this.publishedAt = Instant.now(); touch(); }
    public void archive() { this.status = VideoStatus.ARCHIVED; touch(); }
    private void touch() { this.updatedAt = Instant.now(); }
}
