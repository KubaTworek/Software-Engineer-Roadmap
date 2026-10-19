package com.example.autocomplete.index;

import com.example.autocomplete.model.Suggestion;
import com.example.autocomplete.model.SuggestionMetrics;
import com.example.autocomplete.policy.SafetyPolicyFilter;
import com.example.autocomplete.service.CanonicalKeyGenerator;
import com.example.autocomplete.service.TextNormalizer;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Ograniczona, wersjonowana nakładka near-real-time na batchowy snapshot.
 * Sequence chroni przed reorderem, TTL przed wiecznym życiem trendu, a policy
 * przy ingestion ogranicza czas obecności szkodliwych danych w serving path.
 * Finalna bramka policy w AutocompleteService nadal pozostaje obowiązkowa.
 */
@Component
public class RealtimeDeltaIndex {
    private static final int MAX_ENTRIES = 10_000;
    private static final Duration DEFAULT_TTL = Duration.ofMinutes(10);

    private final TextNormalizer normalizer;
    private final CanonicalKeyGenerator keyGenerator;
    private final SafetyPolicyFilter policy;
    private final Clock clock;
    private final ConcurrentHashMap<String, DeltaEntry> entries = new ConcurrentHashMap<>();
    private final AtomicLong version = new AtomicLong();
    private final AtomicLong generatedSequence = new AtomicLong();

    public RealtimeDeltaIndex(TextNormalizer normalizer, CanonicalKeyGenerator keyGenerator,
                              SafetyPolicyFilter policy, Clock clock) {
        this.normalizer = normalizer;
        this.keyGenerator = keyGenerator;
        this.policy = policy;
        this.clock = clock;
        add(new Suggestion(
                "delta-ai-laptop", "AI Laptop Deals", "trending",
                new SuggestionMetrics(300, .35, .18, .95, .85),
                List.of("ai laptop", "laptop ai"),
                java.util.Set.of("electronics"), java.util.Set.of("en-US"),
                java.util.Set.of("US"), false
        ), generatedSequence.incrementAndGet(), DEFAULT_TTL);
    }

    public boolean add(Suggestion suggestion, long sequence, Duration ttl) {
        if (suggestion == null || ttl == null || ttl.isNegative() || ttl.isZero()) return false;
        if (!policy.evaluate(suggestion).allowed()) return false;
        String key = keyGenerator.canonicalKey(suggestion.displayText());
        Instant expiresAt = clock.instant().plus(ttl);
        final boolean[] accepted = {false};
        entries.compute(key, (ignored, current) -> {
            if (current != null && sequence <= current.sequence()) return current;
            accepted[0] = true;
            return new DeltaEntry(suggestion, sequence, expiresAt);
        });
        if (accepted[0]) {
            version.incrementAndGet();
            evictIfOversized();
        }
        return accepted[0];
    }

    public boolean add(Suggestion suggestion) {
        return add(suggestion, generatedSequence.incrementAndGet(), DEFAULT_TTL);
    }

    public List<Suggestion> candidates(String rawQuery, int limit) {
        purgeExpired();
        String query = normalizer.normalize(rawQuery);
        return entries.values().stream()
                .map(DeltaEntry::suggestion)
                .filter(s -> normalizer.normalize(s.displayText()).startsWith(query)
                        || s.aliases().stream().anyMatch(alias -> normalizer.normalize(alias).startsWith(query)))
                .sorted(Comparator.comparingInt(Suggestion::popularity).reversed()
                        .thenComparing(Suggestion::displayText))
                .limit(Math.max(0, limit))
                .toList();
    }

    public int size() {
        purgeExpired();
        return entries.size();
    }

    public long version() {
        return version.get();
    }

    private void purgeExpired() {
        Instant now = clock.instant();
        int before = entries.size();
        entries.entrySet().removeIf(entry -> !entry.getValue().expiresAt().isAfter(now));
        if (entries.size() != before) version.incrementAndGet();
    }

    private void evictIfOversized() {
        int excess = entries.size() - MAX_ENTRIES;
        if (excess <= 0) return;
        entries.entrySet().stream()
                .sorted(Comparator.comparing(entry -> entry.getValue().expiresAt()))
                .limit(excess)
                .map(java.util.Map.Entry::getKey)
                .toList()
                .forEach(entries::remove);
        version.incrementAndGet();
    }

    private record DeltaEntry(Suggestion suggestion, long sequence, Instant expiresAt) {}
}
