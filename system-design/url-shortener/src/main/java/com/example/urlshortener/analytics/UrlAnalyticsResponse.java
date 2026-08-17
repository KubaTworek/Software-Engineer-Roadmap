package com.example.urlshortener.analytics;

import java.time.LocalDate;
import java.util.List;

public record UrlAnalyticsResponse(
    String shortCode,
    long totalClicks,
    long suspiciousClicks,
    LocalDate from,
    LocalDate to,
    List<DailyClicksResponse> dailyClicks,
    List<DimensionCount> topCountries,
    List<DimensionCount> topDevices,
    List<DimensionCount> topBrowsers,
    List<DimensionCount> topReferrers
) {}
