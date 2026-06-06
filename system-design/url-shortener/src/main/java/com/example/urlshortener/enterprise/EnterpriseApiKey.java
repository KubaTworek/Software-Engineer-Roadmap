package com.example.urlshortener.enterprise;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "enterprise_api_keys", indexes = {
    @Index(name = "idx_enterprise_api_keys_key_hash", columnList = "key_hash", unique = true),
    @Index(name = "idx_enterprise_api_keys_status", columnList = "status")
})
public class EnterpriseApiKey {
    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "enterprise_api_key_id_seq")
    @SequenceGenerator(name = "enterprise_api_key_id_seq", sequenceName = "enterprise_api_key_id_seq", allocationSize = 10)
    private Long id;

    @Column(name = "name", nullable = false, length = 128)
    private String name;

    @Column(name = "key_hash", nullable = false, unique = true, length = 128)
    private String keyHash;

    @Column(name = "status", nullable = false, length = 32)
    private String status = "ACTIVE";

    @Column(name = "tier", nullable = false, length = 64)
    private String tier = "ENTERPRISE";

    @Column(name = "rate_limit_per_minute", nullable = false)
    private int rateLimitPerMinute;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "expires_at")
    private Instant expiresAt;

    protected EnterpriseApiKey() {}

    public EnterpriseApiKey(String name, String keyHash, String tier, int rateLimitPerMinute, Instant expiresAt) {
        this.name = name;
        this.keyHash = keyHash;
        this.tier = tier;
        this.rateLimitPerMinute = rateLimitPerMinute;
        this.expiresAt = expiresAt;
    }

    @PrePersist
    void onCreate() {
        this.createdAt = Instant.now();
        if (this.status == null) this.status = "ACTIVE";
    }

    public Long getId() { return id; }
    public String getName() { return name; }
    public String getKeyHash() { return keyHash; }
    public String getStatus() { return status; }
    public String getTier() { return tier; }
    public int getRateLimitPerMinute() { return rateLimitPerMinute; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getExpiresAt() { return expiresAt; }

    public boolean isActive(Instant now) {
        return "ACTIVE".equals(status) && (expiresAt == null || expiresAt.isAfter(now));
    }
}
