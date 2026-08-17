package com.example.autocomplete.index;

public record IndexStats(String version, int suggestions, int indexedVariants, int trieNodes, int maxCandidatesPerNode,
                         int maxPopularity) {
}
