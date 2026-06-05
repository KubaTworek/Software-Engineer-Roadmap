package com.example.videostreaming.watch;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "watch_progress")
public class WatchProgress {
    @EmbeddedId
    private WatchProgressId id;
    @Column(name = "position_seconds", nullable = false)
    private int positionSeconds;
    @Column(name = "duration_seconds")
    private Integer durationSeconds;
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected WatchProgress() {}
    public WatchProgress(UUID userId, UUID videoId, int positionSeconds, Integer durationSeconds) {
        this.id = new WatchProgressId(userId, videoId);
        update(positionSeconds, durationSeconds);
    }
    public void update(int positionSeconds, Integer durationSeconds) {
        this.positionSeconds = Math.max(positionSeconds, 0);
        this.durationSeconds = durationSeconds;
        this.updatedAt = Instant.now();
    }
    public WatchProgressId getId() { return id; }
    public int getPositionSeconds() { return positionSeconds; }
    public Integer getDurationSeconds() { return durationSeconds; }
    public Instant getUpdatedAt() { return updatedAt; }
}
