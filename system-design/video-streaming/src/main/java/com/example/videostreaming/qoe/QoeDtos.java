package com.example.videostreaming.qoe;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PastOrPresent;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public final class QoeDtos {
    private QoeDtos() {}

    public record IngestQoeRequest(
            UUID eventId,
            @NotNull UUID videoId,
            @NotBlank @Size(max = 128) String sessionId,
            @NotBlank @Pattern(regexp = "playback_started|first_frame|rebuffer_started|rebuffer_ended|bitrate_changed|playback_error|playback_completed") String eventType,
            @PositiveOrZero Integer startupTimeMs,
            @PositiveOrZero Integer rebufferTimeMs,
            @PositiveOrZero Integer bitrateKbps,
            @Size(max = 40) String cdnProvider,
            @Size(max = 40) String player,
            @Size(max = 40) String deviceType,
            @Pattern(regexp = "[A-Za-z]{2}") String country,
            @Size(max = 20) Map<String, Object> attributes,
            @PastOrPresent Instant occurredAt
    ) {}

    public record IngestQoeResponse(UUID eventId, String status) {}
}
