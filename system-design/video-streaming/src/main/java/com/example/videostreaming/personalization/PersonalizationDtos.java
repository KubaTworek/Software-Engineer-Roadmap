package com.example.videostreaming.personalization;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class PersonalizationDtos {
    private PersonalizationDtos() {}

    public record TrackEventRequest(
            UUID eventId,
            @NotBlank String eventType,
            UUID videoId,
            String sessionId,
            String source,
            String deviceType,
            String country,
            Map<String, Object> attributes,
            Instant occurredAt
    ) {}

    public record TrackEventResponse(UUID eventId, String status) {}

    public record VideoRecommendation(
            UUID videoId,
            String title,
            String reason,
            double score,
            String algorithm,
            String experimentVariant
    ) {}

    public record RecommendationResponse(
            UUID userId,
            String algorithm,
            String experimentVariant,
            List<VideoRecommendation> items
    ) {}

    public record TrendingVideo(
            UUID videoId,
            String title,
            long views,
            long starts,
            long completions,
            long uniqueUsers,
            double score
    ) {}

    public record TrendingResponse(int windowHours, List<TrendingVideo> items) {}

    public record RankingResponse(String surface, String algorithm, String experimentVariant, List<VideoRecommendation> items) {}

    public record AssignmentResponse(String experimentKey, String variantKey, boolean newlyAssigned) {}

    public record FeatureRecomputeResponse(String status, int userFeaturesUpdated, int videoFeaturesUpdated, int warehouseRowsUpdated) {}

    public record UserFeatureResponse(UUID userId, String favoriteCategory, int watchedVideos30d, int completedVideos30d, double avgCompletionRate, Instant updatedAt) {}

    public record VideoFeatureResponse(UUID videoId, long views7d, long views30d, double completionRate7d, double qualityScore7d, double trendingScore, Instant updatedAt) {}

    public record DailyVideoMetric(LocalDate metricDate, UUID videoId, long views, long starts, long completions, long uniqueUsers, double avgStartupMs, double rebufferRatio) {}

    public record DailyVideoMetricsResponse(List<DailyVideoMetric> items) {}
}
