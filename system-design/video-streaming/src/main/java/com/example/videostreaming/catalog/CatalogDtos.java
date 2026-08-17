package com.example.videostreaming.catalog;

import com.example.videostreaming.premium.SubscriptionPlanCode;
import jakarta.validation.constraints.NotBlank;
import java.io.Serializable;
import java.time.Instant;
import java.util.UUID;

public final class CatalogDtos {
    private CatalogDtos() {}
    public record CreateVideoRequest(@NotBlank String title, String description) {}
    public record UpdateVideoRequest(@NotBlank String title, String description) {}
    public record VideoResponse(UUID id, String title, String description, VideoStatus status, VideoVisibility visibility,
                                Integer durationSeconds, String playbackReady, Instant publishedAt,
                                SubscriptionPlanCode minimumPlanCode, String allowedCountries,
                                boolean drmProtected, String licensePolicy) implements Serializable {
        public static VideoResponse from(Video video) {
            return new VideoResponse(video.getId(), video.getTitle(), video.getDescription(), video.getStatus(),
                    video.getVisibility(), video.getDurationSeconds(), video.getHlsMasterObjectKey() != null ? "YES" : "NO",
                    video.getPublishedAt(), video.getMinimumPlanCode(), video.getAllowedCountries(),
                    video.isDrmProtected(), video.getLicensePolicy());
        }
    }
}
