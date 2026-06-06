package com.example.urlshortener.analytics;

import java.time.LocalDate;

public record DailyClicksResponse(LocalDate date, long clicks) {}
