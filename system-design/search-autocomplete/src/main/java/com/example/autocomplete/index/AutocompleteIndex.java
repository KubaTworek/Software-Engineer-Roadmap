package com.example.autocomplete.index;

import com.example.autocomplete.model.Suggestion;

import java.util.List;

public interface AutocompleteIndex {
    List<Suggestion> candidates(String rawQuery, int candidateLimit);

    IndexStats stats();

    String version();
}
