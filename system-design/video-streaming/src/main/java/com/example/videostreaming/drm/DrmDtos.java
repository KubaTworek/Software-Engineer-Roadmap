package com.example.videostreaming.drm;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.UUID;

public final class DrmDtos {
    private DrmDtos() {}
    public record LicenseRequest(@NotNull UUID videoId, @NotBlank String playbackToken,
                                 @NotBlank String drmSystem, String deviceId) {}
    public record LicenseResponse(UUID videoId, String drmSystem, String license, String policy, Instant expiresAt) {}
}
