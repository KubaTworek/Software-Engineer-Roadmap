package com.example.autocomplete.model;

public record RankedSuggestion(
        Suggestion suggestion,
        double score,
        double personalizationScore,
        double localeScore,
        double trendingScore,
        double sessionScore,
        double experimentBoost,
        String matchSource
) {
}
