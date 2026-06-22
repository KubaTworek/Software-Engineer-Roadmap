package com.example.autocomplete.model;

import java.util.List;
import java.util.Set;

public record Suggestion(
        String id,
        String displayText,
        String type,
        SuggestionMetrics metrics,
        List<String> aliases,
        Set<String> categories,
        Set<String> locales,
        Set<String> countries,
        boolean manuallyBlocked
) {
    public Suggestion {
        if (id == null || id.isBlank()) throw new IllegalArgumentException("id cannot be blank");
        if (displayText == null || displayText.isBlank())
            throw new IllegalArgumentException("displayText cannot be blank");
        if (type == null || type.isBlank()) throw new IllegalArgumentException("type cannot be blank");
        aliases = aliases == null ? List.of() : List.copyOf(aliases);
        categories = categories == null ? Set.of() : Set.copyOf(categories);
        locales = locales == null ? Set.of() : Set.copyOf(locales);
        countries = countries == null ? Set.of() : Set.copyOf(countries);
    }

    public int popularity() {
        return metrics.popularity();
    }
}
