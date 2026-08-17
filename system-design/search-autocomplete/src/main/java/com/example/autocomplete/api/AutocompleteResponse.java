package com.example.autocomplete.api;

import java.util.List;

public record AutocompleteResponse(String query, int limit, int count, String cacheStatus, String indexVersion,
                                   String experimentVariant, long latencyMs, List<SuggestionResponse> suggestions) {
}
