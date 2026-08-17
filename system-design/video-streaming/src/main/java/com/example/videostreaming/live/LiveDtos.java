package com.example.videostreaming.live;

import jakarta.validation.constraints.NotBlank;

import java.time.Instant;
import java.util.UUID;

public final class LiveDtos {
    private LiveDtos() {}

    public record CreateLiveRequest(
            @NotBlank String title,
            String description,
            LiveLatencyMode latencyMode,
            Boolean dvrEnabled,
            Integer dvrWindowSeconds,
            Boolean recordingEnabled
    ) {}

    public record UpdateLiveRequest(
            String title,
            String description,
            LiveLatencyMode latencyMode,
            Boolean dvrEnabled,
            Integer dvrWindowSeconds,
            Boolean recordingEnabled
    ) {}

    public record LiveStreamResponse(
            UUID id,
            String title,
            String description,
            LiveStatus status,
            LiveLatencyMode latencyMode,
            String ingestUrl,
            String streamKey,
            boolean dvrEnabled,
            int dvrWindowSeconds,
            boolean recordingEnabled,
            String playbackUrl,
            UUID vodVideoId,
            Instant startedAt,
            Instant endedAt,
            String lastError
    ) {}

    public record LivePlaybackResponse(
            UUID liveStreamId,
            String playbackUrl,
            LiveStatus status,
            LiveLatencyMode latencyMode,
            boolean dvrEnabled,
            int dvrWindowSeconds,
            boolean lowLatency,
            String expiresAt
    ) {}

    public record LiveToVodResponse(UUID liveStreamId, UUID videoId, String status) {}
}
