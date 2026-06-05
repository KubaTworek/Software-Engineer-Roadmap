package com.example.videostreaming.watch;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.io.Serializable;
import java.util.UUID;

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
}
