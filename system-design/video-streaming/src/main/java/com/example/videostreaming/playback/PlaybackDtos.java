package com.example.videostreaming.playback;

import java.time.Instant;
import java.util.UUID;

public final class PlaybackDtos {
    private PlaybackDtos() {}
    public record PlaybackResponse(UUID videoId, String format, String playbackUrl, String cdnUrl, Instant expiresAt) {}
}
