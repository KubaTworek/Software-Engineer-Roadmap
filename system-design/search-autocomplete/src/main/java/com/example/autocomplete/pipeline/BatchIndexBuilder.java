package com.example.autocomplete.pipeline;

import com.example.autocomplete.index.SuggestionTrieIndex;
import com.example.autocomplete.model.Suggestion;
import com.example.autocomplete.service.CanonicalKeyGenerator;
import com.example.autocomplete.service.TextNormalizer;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;

@Component
public class BatchIndexBuilder {
    private final TextNormalizer normalizer;
    private final CanonicalKeyGenerator keyGenerator;

    public BatchIndexBuilder(TextNormalizer normalizer, CanonicalKeyGenerator keyGenerator) {
        this.normalizer = normalizer;
        this.keyGenerator = keyGenerator;
    }

    public SuggestionTrieIndex buildSnapshot(List<Suggestion> suggestions) {
        String version = "batch-" + Instant.now().toEpochMilli();
        return new SuggestionTrieIndex(version, suggestions, normalizer, keyGenerator, 200);
    }
}
