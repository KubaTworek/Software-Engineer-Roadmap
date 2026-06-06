package com.example.urlshortener.analytics;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.analytics")
public record AnalyticsProperties(
    boolean enabled,
    String ipHashSalt
) {}
