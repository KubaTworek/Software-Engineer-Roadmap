package com.example.autocomplete.api;

public record SuggestionResponse(String id, String text, String type, int popularity, double score,
                                 double personalizationScore, double localeScore, double trendingScore,
                                 double sessionScore, double experimentBoost, String matchSource) {
}
