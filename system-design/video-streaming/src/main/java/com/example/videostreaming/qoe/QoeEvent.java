package com.example.videostreaming.qoe;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "qoe_events")
public class QoeEvent {
    @Id
    private UUID id;
    @Column(name = "user_id")
    private UUID userId;
    @Column(name = "video_id", nullable = false)
    private UUID videoId;
    @Column(name = "session_id", nullable = false)
    private String sessionId;
    @Column(name = "event_type", nullable = false)
    private String eventType;
    @Column(name = "startup_time_ms")
    private Integer startupTimeMs;
    @Column(name = "rebuffer_time_ms")
    private Integer rebufferTimeMs;
    @Column(name = "bitrate_kbps")
    private Integer bitrateKbps;
    @Column(name = "cdn_provider")
    private String cdnProvider;
    private String player;
    @Column(name = "device_type")
    private String deviceType;
    private String country;
    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;
    @Column(name = "ingested_at", nullable = false)
    private Instant ingestedAt;

    protected QoeEvent() {}

    public QoeEvent(UUID id, UUID userId, UUID videoId, String sessionId, String eventType, Integer startupTimeMs,
                    Integer rebufferTimeMs, Integer bitrateKbps, String cdnProvider, String player, String deviceType,
                    String country, Instant occurredAt) {
        this.id = id == null ? UUID.randomUUID() : id;
        this.userId = userId;
        this.videoId = videoId;
        this.sessionId = sessionId;
        this.eventType = eventType;
        this.startupTimeMs = startupTimeMs;
        this.rebufferTimeMs = rebufferTimeMs;
        this.bitrateKbps = bitrateKbps;
        this.cdnProvider = cdnProvider;
        this.player = player;
        this.deviceType = deviceType;
        this.country = country;
        this.occurredAt = occurredAt == null ? Instant.now() : occurredAt;
        this.ingestedAt = Instant.now();
    }

    public UUID getId() { return id; }
}
