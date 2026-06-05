package com.example.autocomplete.api;

import java.util.List;

public record AutocompleteResponse(
        String query,
        int limit,
        int count,
        List<SuggestionResponse> suggestions
) {
}
