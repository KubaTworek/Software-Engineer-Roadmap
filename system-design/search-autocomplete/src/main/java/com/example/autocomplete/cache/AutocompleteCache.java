package com.example.autocomplete.cache;

import com.example.autocomplete.model.Suggestion;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

/**
 * L1 cache współdzielonych kandydatów, nie spersonalizowanej odpowiedzi.
 * Dzięki temu gorący prefiks ma wysoki hit-rate, ale ranking użytkownika i finalna
 * polityka są wykonywane dla każdego requestu i nie mogą przeciec między sesjami.
 */
@Component
public class AutocompleteCache {
    private final Cache<String, List<Suggestion>> candidates = Caffeine.newBuilder()
            .maximumSize(50_000)
            .expireAfterWrite(Duration.ofSeconds(45))
            .recordStats()
            .build();

    public Optional<List<Suggestion>> getCandidates(String key) {
        return Optional.ofNullable(candidates.getIfPresent(key));
    }

    public void putCandidates(String key, List<Suggestion> value) {
        candidates.put(key, List.copyOf(value));
    }

    public String candidateKey(String query, String locale, String country, String category,
                               String indexVersion, long deltaVersion, long policyVersion, int limit) {
        return String.join(":", "candidates-v1", safe(query), safe(locale), safe(country), safe(category),
                indexVersion, Long.toString(deltaVersion), Long.toString(policyVersion), Integer.toString(limit));
    }

    public long estimatedSize() {
        return candidates.estimatedSize();
    }

    private String safe(String value) {
        return value == null || value.isBlank() ? "_" : value;
    }
}
