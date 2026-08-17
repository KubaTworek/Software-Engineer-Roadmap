package com.example.videostreaming.personalization;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.personalization")
public record PersonalizationProperties(
        boolean enabled,
        int defaultRecommendationLimit,
        int trendingWindowHours,
        int candidatePoolSize,
        double trendingWeight,
        double affinityWeight,
        double freshnessWeight
) {}
