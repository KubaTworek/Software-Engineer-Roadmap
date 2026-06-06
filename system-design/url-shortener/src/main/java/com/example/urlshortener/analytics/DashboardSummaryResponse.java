package com.example.urlshortener.analytics;

import java.time.LocalDate;

public record DashboardSummaryResponse(
    long totalUrls,
    long activeUrls,
    long blockedUrls,
    long totalClicks,
    long clicksToday,
    LocalDate today
) {}
