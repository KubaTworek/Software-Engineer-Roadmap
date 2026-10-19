package com.example.autocomplete.service;

import com.example.autocomplete.abtest.ExperimentAssignmentService;
import com.example.autocomplete.abuse.AbuseDetectionService;
import com.example.autocomplete.cache.AutocompleteCache;
import com.example.autocomplete.index.RealtimeDeltaIndex;
import com.example.autocomplete.index.SuggestionTrieIndex;
import com.example.autocomplete.model.Suggestion;
import com.example.autocomplete.model.SuggestionMetrics;
import com.example.autocomplete.personalization.SearchHistoryStore;
import com.example.autocomplete.personalization.UserProfileStore;
import com.example.autocomplete.pipeline.EventSanitizer;
import com.example.autocomplete.pipeline.InMemoryQueryTelemetrySink;
import com.example.autocomplete.policy.SafetyPolicyFilter;
import com.example.autocomplete.privacy.QueryPrivacyService;
import com.example.autocomplete.ranking.PrefixMatchScorer;
import com.example.autocomplete.ranking.SuggestionRanker;
import com.example.autocomplete.rollout.IndexRegistry;
import com.example.autocomplete.trending.TrendingStore;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.*;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;

class AutocompleteServiceTest {
    private final Clock clock = Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC);
    private final TextNormalizer normalizer = new TextNormalizer();
    private final QueryPrivacyService privacy = new QueryPrivacyService("test-privacy-key-123456789");
    private final SafetyPolicyFilter policy = new SafetyPolicyFilter(normalizer, privacy);
    private final CanonicalKeyGenerator keys = new CanonicalKeyGenerator(normalizer);
    private final IndexRegistry registry = new IndexRegistry();
    private final RealtimeDeltaIndex delta = new RealtimeDeltaIndex(normalizer, keys, policy, clock);
    private AutocompleteService service;

    @BeforeEach
    void setUp() {
        Suggestion iphone = suggestion("base-iphone", "iPhone 15", Set.of("en-US"), Set.of("US"));
        registry.register(new SuggestionTrieIndex("index-v1", List.of(iphone), normalizer, keys, 20));
        var history = new SearchHistoryStore(privacy);
        service = new AutocompleteService(
                registry,
                delta,
                new SuggestionRanker(new PrefixMatchScorer(normalizer), new UserProfileStore(), history,
                        new TrendingStore(), normalizer),
                normalizer,
                keys,
                new com.example.autocomplete.language.LanguageDetector(),
                policy,
                new AbuseDetectionService(clock),
                new ExperimentAssignmentService(),
                history,
                new AutocompleteCache(),
                new EventSanitizer(privacy),
                new InMemoryQueryTelemetrySink(),
                clock,
                new SimpleMeterRegistry()
        );
    }

    @Test
    void cacheCannotBypassNewSafetyPolicy() {
        assertThat(search("iph").suggestions()).hasSize(1);
        assertThat(search("iph").cacheStatus()).isEqualTo("CANDIDATE_HIT");

        policy.replaceBlockedTerms(Set.of("iphone"));

        assertThat(search("iph").suggestions()).isEmpty();
    }

    @Test
    void mergesSnapshotAndDeltaWithoutReturningLogicalDuplicate() {
        delta.add(suggestion("delta-iphone", "iPhone-15", Set.of("en-US"), Set.of("US")),
                10, Duration.ofMinutes(5));

        assertThat(search("iph").suggestions())
                .extracting(result -> keys.canonicalKey(result.suggestion().displayText()))
                .containsExactly("iphone15");
    }

    @Test
    void usesBoundedFuzzyFallbackOnlyWhenPrefixRetrievalIsEmpty() {
        assertThat(search("iphnoe").suggestions())
                .extracting(result -> result.suggestion().displayText())
                .containsExactly("iPhone 15");
    }

    @Test
    void localeIsRetrievalBoundaryNotOnlyWeakRankingSignal() {
        assertThat(service.autocomplete("iph", 10, "anonymous", "s1", "pl-PL", "PL", null, "127.0.0.1")
                .suggestions()).isEmpty();
    }

    @Test
    void tooShortPrefixDoesNotScanTheIndex() {
        assertThat(search("i").cacheStatus()).isEqualTo("TOO_SHORT");
        assertThat(search("i").suggestions()).isEmpty();
    }

    private AutocompleteService.AutocompleteResult search(String query) {
        return service.autocomplete(query, 10, "anonymous", "session-1", "en-US", "US", null, "127.0.0.1");
    }

    private Suggestion suggestion(String id, String text, Set<String> locales, Set<String> countries) {
        return new Suggestion(id, text, "query", new SuggestionMetrics(1000, .7, .5, .8, .95),
                List.of(), Set.of("electronics"), locales, countries, false);
    }
}
