package com.example.autocomplete.index;

import com.example.autocomplete.model.Suggestion;
import com.example.autocomplete.model.SuggestionMetrics;
import com.example.autocomplete.policy.SafetyPolicyFilter;
import com.example.autocomplete.privacy.QueryPrivacyService;
import com.example.autocomplete.service.CanonicalKeyGenerator;
import com.example.autocomplete.service.TextNormalizer;
import org.junit.jupiter.api.Test;

import java.time.*;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;

class RealtimeDeltaIndexTest {
    private final TextNormalizer normalizer = new TextNormalizer();
    private final CanonicalKeyGenerator keys = new CanonicalKeyGenerator(normalizer);
    private final SafetyPolicyFilter policy = new SafetyPolicyFilter(normalizer,
            new QueryPrivacyService("test-privacy-key-123456789"));

    @Test
    void rejectsReorderedUpdateAndExpiresAcceptedEntry() {
        MutableClock clock = new MutableClock(Instant.parse("2026-01-01T00:00:00Z"));
        RealtimeDeltaIndex index = new RealtimeDeltaIndex(normalizer, keys, policy, clock);

        assertThat(index.add(suggestion("new", 900), 20, Duration.ofSeconds(30))).isTrue();
        assertThat(index.add(suggestion("old", 100), 19, Duration.ofMinutes(5))).isFalse();
        assertThat(index.candidates("hot", 10)).extracting(Suggestion::id).containsExactly("new");

        clock.advance(Duration.ofSeconds(31));
        assertThat(index.candidates("hot", 10)).isEmpty();
    }

    @Test
    void blocksUnsafeDeltaAtIngestion() {
        RealtimeDeltaIndex index = new RealtimeDeltaIndex(normalizer, keys, policy, Clock.systemUTC());
        policy.replaceBlockedTerms(Set.of("hot topic"));

        assertThat(index.add(suggestion("unsafe", 900), 20, Duration.ofMinutes(1))).isFalse();
    }

    private Suggestion suggestion(String id, int popularity) {
        return new Suggestion(id, "Hot Topic", "query", new SuggestionMetrics(popularity, .5, .2, .9, .9),
                List.of(), Set.of(), Set.of(), Set.of(), false);
    }

    private static final class MutableClock extends Clock {
        private Instant instant;
        private MutableClock(Instant instant) { this.instant = instant; }
        void advance(Duration duration) { instant = instant.plus(duration); }
        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() { return instant; }
    }
}
