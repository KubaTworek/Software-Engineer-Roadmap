package com.example.videostreaming.live;

import com.example.videostreaming.auth.User;
import com.example.videostreaming.catalog.Video;
import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "live_streams")
public class LiveStream {
    @Id
    private UUID id;

    @Column(nullable = false)
    private String title;

    private String description;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "owner_id")
    private User owner;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private LiveStatus status;

    @Enumerated(EnumType.STRING)
    @Column(name = "latency_mode", nullable = false)
    private LiveLatencyMode latencyMode;

    @Column(name = "stream_key", nullable = false, unique = true)
    private String streamKey;

    @Column(name = "ingest_url")
    private String ingestUrl;

    @Column(name = "internal_ingest_url")
    private String internalIngestUrl;

    @Column(name = "hls_master_object_key")
    private String hlsMasterObjectKey;

    @Column(name = "dvr_enabled", nullable = false)
    private boolean dvrEnabled;

    @Column(name = "dvr_window_seconds", nullable = false)
    private int dvrWindowSeconds;

    @Column(name = "recording_enabled", nullable = false)
    private boolean recordingEnabled;

    @Column(name = "recording_object_key")
    private String recordingObjectKey;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "vod_video_id")
    private Video vodVideo;

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "ended_at")
    private Instant endedAt;

    @Column(name = "last_error")
    private String lastError;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected LiveStream() {}

    public LiveStream(String title, String description, User owner, LiveLatencyMode latencyMode,
                      String streamKey, String ingestUrl, String internalIngestUrl,
                      boolean dvrEnabled, int dvrWindowSeconds, boolean recordingEnabled) {
        this.id = UUID.randomUUID();
        this.title = title;
        this.description = description;
        this.owner = owner;
        this.status = LiveStatus.SCHEDULED;
        this.latencyMode = latencyMode == null ? LiveLatencyMode.STANDARD : latencyMode;
        this.streamKey = streamKey;
        this.ingestUrl = ingestUrl;
        this.internalIngestUrl = internalIngestUrl;
        this.dvrEnabled = dvrEnabled;
        this.dvrWindowSeconds = dvrWindowSeconds;
        this.recordingEnabled = recordingEnabled;
        this.createdAt = Instant.now();
        this.updatedAt = createdAt;
    }

    public UUID getId() { return id; }
    public String getTitle() { return title; }
    public String getDescription() { return description; }
    public User getOwner() { return owner; }
    public LiveStatus getStatus() { return status; }
    public LiveLatencyMode getLatencyMode() { return latencyMode; }
    public String getStreamKey() { return streamKey; }
    public String getIngestUrl() { return ingestUrl; }
    public String getInternalIngestUrl() { return internalIngestUrl; }
    public String getHlsMasterObjectKey() { return hlsMasterObjectKey; }
    public boolean isDvrEnabled() { return dvrEnabled; }
    public int getDvrWindowSeconds() { return dvrWindowSeconds; }
    public boolean isRecordingEnabled() { return recordingEnabled; }
    public String getRecordingObjectKey() { return recordingObjectKey; }
    public Video getVodVideo() { return vodVideo; }
    public Instant getStartedAt() { return startedAt; }
    public Instant getEndedAt() { return endedAt; }
    public String getLastError() { return lastError; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }

    public void update(String title, String description, LiveLatencyMode latencyMode, Boolean dvrEnabled, Integer dvrWindowSeconds, Boolean recordingEnabled) {
        if (title != null && !title.isBlank()) this.title = title;
        this.description = description;
        if (latencyMode != null) this.latencyMode = latencyMode;
        if (dvrEnabled != null) this.dvrEnabled = dvrEnabled;
        if (dvrWindowSeconds != null && dvrWindowSeconds > 0) this.dvrWindowSeconds = dvrWindowSeconds;
        if (recordingEnabled != null) this.recordingEnabled = recordingEnabled;
        touch();
    }

    public void markStarting() { this.status = LiveStatus.STARTING; this.lastError = null; touch(); }
    public void markLive(String hlsMasterObjectKey, String recordingObjectKey) {
        this.status = LiveStatus.LIVE;
        this.hlsMasterObjectKey = hlsMasterObjectKey;
        this.recordingObjectKey = recordingObjectKey;
        this.startedAt = this.startedAt == null ? Instant.now() : this.startedAt;
        this.lastError = null;
        touch();
    }
    public void markStopping() { this.status = LiveStatus.STOPPING; touch(); }
    public void markEnded() { this.status = LiveStatus.ENDED; this.endedAt = Instant.now(); touch(); }
    public void markFailed(String error) { this.status = LiveStatus.FAILED; this.lastError = error; touch(); }
    public void attachVod(Video video) { this.vodVideo = video; this.status = LiveStatus.VOD_READY; touch(); }

    private void touch() { this.updatedAt = Instant.now(); }
}
