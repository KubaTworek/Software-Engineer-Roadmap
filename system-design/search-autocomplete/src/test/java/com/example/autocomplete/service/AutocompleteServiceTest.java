/*
package com.example.autocomplete.service;

import com.example.autocomplete.model.Suggestion;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class AutocompleteServiceTest {

    private final AutocompleteService autocompleteService = new AutocompleteService(
            List.of(
                    new Suggestion("iphone 15", "query", 1000),
                    new Suggestion("iphone case", "query", 800),
                    new Suggestion("ipad pro", "query", 700),
                    new Suggestion("java", "query", 900)
            ),
            new TextNormalizer()
    );

    @Test
    void shouldReturnSuggestionsMatchingPrefix() {
        List<Suggestion> result = autocompleteService.autocomplete("iph", 10);

        assertThat(result)
                .extracting(Suggestion::text)
                .containsExactly("iphone 15", "iphone case");
    }

    @Test
    void shouldReturnEmptyListForTooShortQuery() {
        List<Suggestion> result = autocompleteService.autocomplete("i", 10);

        assertThat(result).isEmpty();
    }

    @Test
    void shouldSortByPopularityDescending() {
        List<Suggestion> result = autocompleteService.autocomplete("ip", 10);

        assertThat(result)
                .extracting(Suggestion::text)
                .containsExactly("iphone 15", "iphone case", "ipad pro");
    }

    @Test
    void shouldLimitResults() {
        List<Suggestion> result = autocompleteService.autocomplete("ip", 2);

        assertThat(result).hasSize(2);
    }

    @Test
    void shouldSanitizeInvalidLimit() {
        assertThat(autocompleteService.sanitizeLimit(null)).isEqualTo(10);
        assertThat(autocompleteService.sanitizeLimit(0)).isEqualTo(10);
        assertThat(autocompleteService.sanitizeLimit(100)).isEqualTo(20);
    }
}
*/
