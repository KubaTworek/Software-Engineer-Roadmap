package com.example.urlshortener.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "url_moderation_audit")
public class UrlModerationAudit {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name = "short_code", nullable = false, length = 32) private String shortCode;
    @Column(name = "action", nullable = false, length = 32) private String action;
    @Column(name = "actor_id", nullable = false, length = 128) private String actorId;
    @Column(name = "reason", columnDefinition = "TEXT") private String reason;
    @Column(name = "occurred_at", nullable = false) private Instant occurredAt;
    @Column(name = "resulting_version", nullable = false) private long resultingVersion;
    protected UrlModerationAudit() {}
    public UrlModerationAudit(String shortCode, String action, String actorId, String reason, Instant occurredAt, long resultingVersion) {
        this.shortCode = shortCode; this.action = action; this.actorId = actorId;
        this.reason = reason; this.occurredAt = occurredAt; this.resultingVersion = resultingVersion;
    }
}
