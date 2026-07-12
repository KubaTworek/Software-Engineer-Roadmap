package com.example.videostreaming.catalog;

import com.example.videostreaming.auth.User;
import com.example.videostreaming.premium.SubscriptionPlanCode;
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

    @Enumerated(EnumType.STRING)
    @Column(name = "minimum_plan_code", nullable = false)
    private SubscriptionPlanCode minimumPlanCode = SubscriptionPlanCode.FREE;

    @Column(name = "allowed_countries")
    private String allowedCountries;

    @Column(name = "drm_protected", nullable = false)
    private boolean drmProtected = false;

    @Column(name = "license_policy", nullable = false)
    private String licensePolicy = "STREAMING_ONLY";

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
    public SubscriptionPlanCode getMinimumPlanCode() { return minimumPlanCode == null ? SubscriptionPlanCode.FREE : minimumPlanCode; }
    public String getAllowedCountries() { return allowedCountries; }
    public boolean isDrmProtected() { return drmProtected; }
    public String getLicensePolicy() { return licensePolicy; }

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

    public void updatePremiumPolicy(SubscriptionPlanCode minimumPlanCode, String allowedCountries, Boolean drmProtected, String licensePolicy) {
        if (minimumPlanCode != null) this.minimumPlanCode = minimumPlanCode;
        this.allowedCountries = allowedCountries;
        if (drmProtected != null) this.drmProtected = drmProtected;
        if (licensePolicy != null && !licensePolicy.isBlank()) this.licensePolicy = licensePolicy;
        touch();
    }
    private void touch() { this.updatedAt = Instant.now(); }
}
