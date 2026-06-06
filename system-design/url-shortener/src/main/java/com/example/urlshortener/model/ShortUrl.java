package com.example.urlshortener.model;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(
    name = "urls",
    indexes = {
        @Index(name = "idx_urls_user_id", columnList = "user_id"),
        @Index(name = "idx_urls_expires_at", columnList = "expires_at"),
        @Index(name = "idx_urls_status", columnList = "status")
    }
)
public class ShortUrl {

    @Id
    private Long id;

    @Column(name = "short_code", unique = true, length = 32)
    private String shortCode;

    @Column(name = "long_url", nullable = false, columnDefinition = "TEXT")
    private String longUrl;

    @Column(name = "user_id")
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    private UrlStatus status = UrlStatus.ACTIVE;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "expires_at")
    private Instant expiresAt;

    @Column(name = "blocked_reason", columnDefinition = "TEXT")
    private String blockedReason;

    @Column(name = "blocked_at")
    private Instant blockedAt;

    protected ShortUrl() {}

    public ShortUrl(Long id, String shortCode, String longUrl, Instant expiresAt) {
        this.id = id;
        this.shortCode = shortCode;
        this.longUrl = longUrl;
        this.expiresAt = expiresAt;
        this.status = UrlStatus.ACTIVE;
    }

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        this.createdAt = now;
        this.updatedAt = now;
        if (this.status == null) this.status = UrlStatus.ACTIVE;
    }

    @PreUpdate
    void onUpdate() {
        this.updatedAt = Instant.now();
    }

    public Long getId() { return id; }
    public String getShortCode() { return shortCode; }
    public void setShortCode(String shortCode) { this.shortCode = shortCode; }
    public String getLongUrl() { return longUrl; }
    public Long getUserId() { return userId; }
    public UrlStatus getStatus() { return status; }
    public void setStatus(UrlStatus status) { this.status = status; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public Instant getExpiresAt() { return expiresAt; }
    public String getBlockedReason() { return blockedReason; }
    public Instant getBlockedAt() { return blockedAt; }

    public void block(String reason, Instant blockedAt) {
        this.status = UrlStatus.BLOCKED;
        this.blockedReason = reason;
        this.blockedAt = blockedAt;
    }

    public void unblock() {
        this.status = UrlStatus.ACTIVE;
        this.blockedReason = null;
        this.blockedAt = null;
    }

    public boolean isExpired(Instant now) { return expiresAt != null && expiresAt.isBefore(now); }
}
