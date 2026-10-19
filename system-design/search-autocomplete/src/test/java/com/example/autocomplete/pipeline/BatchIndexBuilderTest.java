package com.example.autocomplete.pipeline;

import com.example.autocomplete.model.Suggestion;
import com.example.autocomplete.model.SuggestionMetrics;
import com.example.autocomplete.service.CanonicalKeyGenerator;
import com.example.autocomplete.service.TextNormalizer;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;

class BatchIndexBuilderTest {
    @Test
    void retryOfSameInputCreatesSameContentAddressedVersion() {
        var normalizer = new TextNormalizer();
        var builder = new BatchIndexBuilder(normalizer, new CanonicalKeyGenerator(normalizer));
        var suggestion = new Suggestion("1", "Java", "query", new SuggestionMetrics(10, .2, .1, .3, .9),
                List.of(), Set.of(), Set.of(), Set.of(), false);

        assertThat(builder.buildSnapshot(List.of(suggestion)).version())
                .isEqualTo(builder.buildSnapshot(List.of(suggestion)).version());
    }
}
