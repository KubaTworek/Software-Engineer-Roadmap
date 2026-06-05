package com.example.videostreaming.watch;

import jakarta.validation.constraints.Min;
import java.time.Instant;
import java.util.UUID;

public final class WatchDtos {
    private WatchDtos() {}
    public record SaveProgressRequest(@Min(0) int positionSeconds, Integer durationSeconds) {}
    public record ProgressResponse(UUID userId, UUID videoId, int positionSeconds, Integer durationSeconds, Instant updatedAt) {
        public static ProgressResponse from(WatchProgress p) {
            return new ProgressResponse(p.getId().getUserId(), p.getId().getVideoId(), p.getPositionSeconds(), p.getDurationSeconds(), p.getUpdatedAt());
        }
    }
}
