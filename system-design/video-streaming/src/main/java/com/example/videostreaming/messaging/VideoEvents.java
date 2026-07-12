package com.example.videostreaming.messaging;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public final class VideoEvents {
    private VideoEvents() {}

    public record TranscodingRequested(UUID jobId, UUID videoId, String sourceObjectKey, int attempt, Instant requestedAt) {}

    public record LiveStartRequested(UUID liveStreamId, String streamKey, Instant requestedAt) {}

    public record LiveStopRequested(UUID liveStreamId, Instant requestedAt) {}

    public record QoePlaybackEvent(
            UUID eventId,
            UUID userId,
            UUID videoId,
            String sessionId,
            String eventType,
            Integer startupTimeMs,
            Integer rebufferTimeMs,
            Integer bitrateKbps,
            String cdnProvider,
            String player,
            String deviceType,
            String country,
            Map<String, Object> attributes,
            Instant occurredAt
    ) {}
}
