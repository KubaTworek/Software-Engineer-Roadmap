package com.example.autocomplete.pipeline;

import com.example.autocomplete.index.SuggestionTrieIndex;
import com.example.autocomplete.model.Suggestion;
import com.example.autocomplete.service.CanonicalKeyGenerator;
import com.example.autocomplete.service.TextNormalizer;
import org.springframework.stereotype.Component;

import java.util.List;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Comparator;

@Component
public class BatchIndexBuilder {
    private final TextNormalizer normalizer;
    private final CanonicalKeyGenerator keyGenerator;

    public BatchIndexBuilder(TextNormalizer normalizer, CanonicalKeyGenerator keyGenerator) {
        this.normalizer = normalizer;
        this.keyGenerator = keyGenerator;
    }

    public SuggestionTrieIndex buildSnapshot(List<Suggestion> suggestions) {
        // Content-addressed version sprawia, że retry tego samego builda daje
        // ten sam artefakt zamiast nowej wersji zależnej od zegara.
        String version = "batch-" + contentHash(suggestions);
        return new SuggestionTrieIndex(version, suggestions, normalizer, keyGenerator, 200);
    }

    private String contentHash(List<Suggestion> suggestions) {
        try {
            String material = suggestions.stream()
                    .sorted(Comparator.comparing(Suggestion::id))
                    .map(suggestion -> suggestion.id() + "|" + suggestion.displayText() + "|"
                            + suggestion.metrics() + "|" + suggestion.manuallyBlocked())
                    .reduce("", (left, right) -> left + "\n" + right);
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(material.getBytes(StandardCharsets.UTF_8))).substring(0, 16);
        } catch (Exception exception) {
            throw new IllegalStateException("Cannot create snapshot version", exception);
        }
    }
}
