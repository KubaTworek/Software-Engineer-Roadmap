package com.example.autocomplete.index;

import com.example.autocomplete.model.Suggestion;

import java.util.List;

public interface AutocompleteIndex {
    List<Suggestion> candidates(String rawQuery, int candidateLimit);

    default List<Suggestion> fuzzyCandidates(String rawQuery, int maxDistance, int candidateLimit) {
        return List.of();
    }

    IndexStats stats();

    String version();
}
