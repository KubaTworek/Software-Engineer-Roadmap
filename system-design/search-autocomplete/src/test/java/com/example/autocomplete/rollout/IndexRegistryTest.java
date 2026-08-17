package com.example.autocomplete.rollout;

import com.example.autocomplete.index.SuggestionTrieIndex;
import com.example.autocomplete.model.*;
import com.example.autocomplete.service.*;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;

class IndexRegistryTest {
    @Test
    void shouldActivateAndRollbackIndex() {
        TextNormalizer n = new TextNormalizer();
        CanonicalKeyGenerator k = new CanonicalKeyGenerator(n);
        Suggestion s = new Suggestion("1", "Java", "query", new SuggestionMetrics(100, .1, .1, .1, .9), List.of(), Set.of(), Set.of(), Set.of(), false);
        IndexRegistry registry = new IndexRegistry();
        registry.register(new SuggestionTrieIndex("v1", List.of(s), n, k, 10));
        registry.register(new SuggestionTrieIndex("v2", List.of(s), n, k, 10));
        registry.activate("v2");
        assertThat(registry.activeVersion()).isEqualTo("v2");
        registry.rollback();
        assertThat(registry.activeVersion()).isEqualTo("v1");
    }
}
