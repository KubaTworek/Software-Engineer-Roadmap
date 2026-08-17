package com.example.autocomplete.model;

public record SuggestionMetrics(int popularity, double ctr, double conversionRate, double freshness, double quality) {
    public SuggestionMetrics {
        if (popularity < 0) throw new IllegalArgumentException("Popularity cannot be negative");
        validate("ctr", ctr);
        validate("conversionRate", conversionRate);
        validate("freshness", freshness);
        validate("quality", quality);
    }

    private static void validate(String name, double v) {
        if (v < 0.0 || v > 1.0) throw new IllegalArgumentException(name + " must be between 0 and 1");
    }
}
