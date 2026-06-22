package com.example.autocomplete.index;

import com.example.autocomplete.model.IndexedSuggestion;

import java.util.*;

final class TrieNode {
    private final Map<Character, TrieNode> children = new HashMap<>();
    private final List<IndexedSuggestion> candidates = new ArrayList<>();

    Map<Character, TrieNode> children() {
        return children;
    }

    List<IndexedSuggestion> candidates() {
        return candidates;
    }
}
