package com.example.autocomplete.service;

import com.example.autocomplete.model.Suggestion;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;

@Service
public class AutocompleteService {
    private static final int DEFAULT_LIMIT = 10;
    private static final int MAX_LIMIT = 20;

    private final List<Suggestion> suggestions;
    private final TextNormalizer textNormalizer;

    public AutocompleteService(List<Suggestion> suggestions, TextNormalizer textNormalizer) {
        this.suggestions = List.copyOf(Objects.requireNonNull(suggestions));
        this.textNormalizer = Objects.requireNonNull(textNormalizer);
    }

    public List<Suggestion> autocomplete(String query, Integer requestedLimit) {
        String normalizedQuery = textNormalizer.normalize(query);
        int limit = sanitizeLimit(requestedLimit);

        if (normalizedQuery.length() < 2) {
            return List.of();
        }

        return suggestions.stream()
                .filter(suggestion -> textNormalizer.normalize(suggestion.text()).startsWith(normalizedQuery))
                .sorted(Comparator
                        .comparingInt(Suggestion::popularity).reversed()
                        .thenComparing(Suggestion::text))
                .limit(limit)
                .toList();
    }

    public int sanitizeLimit(Integer requestedLimit) {
        if (requestedLimit == null) {
            return DEFAULT_LIMIT;
        }

        if (requestedLimit < 1) {
            return DEFAULT_LIMIT;
        }

        return Math.min(requestedLimit, MAX_LIMIT);
    }
}
