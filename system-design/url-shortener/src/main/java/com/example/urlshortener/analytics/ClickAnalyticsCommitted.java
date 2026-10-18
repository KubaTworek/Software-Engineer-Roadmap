package com.example.urlshortener.analytics;

import java.time.LocalDate;

/** Emitted in the SQL transaction and observed only after its successful commit. */
public record ClickAnalyticsCommitted(String shortCode, LocalDate date) {
}
