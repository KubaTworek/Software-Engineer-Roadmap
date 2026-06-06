package com.example.urlshortener.analytics;

import java.time.LocalDate;
import java.util.List;

public record UrlAnalyticsResponse(
    String shortCode,
    long totalClicks,
    LocalDate from,
    LocalDate to,
    List<DailyClicksResponse> dailyClicks
) {}
