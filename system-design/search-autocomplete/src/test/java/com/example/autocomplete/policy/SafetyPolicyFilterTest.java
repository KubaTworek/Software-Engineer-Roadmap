package com.example.autocomplete.policy;

import com.example.autocomplete.model.*;
import com.example.autocomplete.service.TextNormalizer;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;

class SafetyPolicyFilterTest {
    @Test
    void shouldBlockSpamSuggestion() {
        SafetyPolicyFilter filter = new SafetyPolicyFilter(new TextNormalizer());
        Suggestion spam = new Suggestion("1", "iPhone free free free", "query", new SuggestionMetrics(100, .1, .1, .1, .9), List.of(), Set.of(), Set.of(), Set.of(), false);
        assertThat(filter.evaluate(spam).allowed()).isFalse();
    }
}
