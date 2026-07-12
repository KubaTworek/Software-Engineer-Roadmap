package com.example.videostreaming.qoe;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public final class QoeDtos {
    private QoeDtos() {}

    public record IngestQoeRequest(
            UUID eventId,
            @NotNull UUID videoId,
            @NotBlank String sessionId,
            @NotBlank String eventType,
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

    public record IngestQoeResponse(UUID eventId, String status) {}
}
