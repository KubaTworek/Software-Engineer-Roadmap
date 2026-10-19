package com.example.videostreaming.watch;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.io.Serializable;
import java.util.UUID;
import java.util.Objects;

@Embeddable
public class WatchProgressId implements Serializable {
    @Column(name = "user_id")
    private UUID userId;
    @Column(name = "video_id")
    private UUID videoId;

    protected WatchProgressId() {}
    public WatchProgressId(UUID userId, UUID videoId) { this.userId = userId; this.videoId = videoId; }
    public UUID getUserId() { return userId; }
    public UUID getVideoId() { return videoId; }

    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof WatchProgressId that)) return false;
        return Objects.equals(userId, that.userId) && Objects.equals(videoId, that.videoId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(userId, videoId);
    }
}
