package com.example.videostreaming.playback;

import com.example.videostreaming.premium.SubscriptionPlanCode;
import java.io.Serializable;
import java.time.Instant;
import java.util.UUID;

public final class PlaybackDtos {
    private PlaybackDtos() {}
    public record PlaybackResponse(UUID videoId, String format, String playbackUrl, String cdnUrl, Instant expiresAt,
                                   boolean drmRequired, String drmLicenseUrl, String playbackToken,
                                   SubscriptionPlanCode requiredPlan, SubscriptionPlanCode userPlan,
                                   String country, String signedCookieMode) implements Serializable {}
}
