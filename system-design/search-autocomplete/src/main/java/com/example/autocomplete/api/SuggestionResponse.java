package com.example.autocomplete.api;

public record SuggestionResponse(
        String text,
        String type,
        int popularity
) {
}
