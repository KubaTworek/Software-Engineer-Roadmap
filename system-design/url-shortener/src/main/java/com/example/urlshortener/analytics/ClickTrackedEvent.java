package com.example.urlshortener.analytics;

import java.time.Instant;

public record ClickTrackedEvent(
    String shortCode,
    Instant clickedAt,
    String ipAddress,
    String userAgent,
    String referrer
) {}
