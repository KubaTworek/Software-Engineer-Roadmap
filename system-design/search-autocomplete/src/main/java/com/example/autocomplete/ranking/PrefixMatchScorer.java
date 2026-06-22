package com.example.autocomplete.ranking;

import com.example.autocomplete.model.Suggestion;
import com.example.autocomplete.service.TextNormalizer;
import org.springframework.stereotype.Component;

import java.util.Arrays;

/**
 * Liczy score dopasowania tekstowego między query użytkownika a sugestią.
 *
 * To jest jeden z sygnałów używanych przez SuggestionRanker.
 *
 * Przykład:
 * query = "iph"
 * suggestion = "iPhone 15"
 *
 * Wynik powinien być wysoki, bo displayText zaczyna się od query.
 *
 * Ten scorer nie decyduje sam o finalnej kolejności.
 * On tylko zwraca jeden sygnał rankingowy: prefixMatchScore.
 */
@Component
public class PrefixMatchScorer {

    /**
     * Normalizer zapewnia, że porównujemy tekst w tej samej formie.
     *
     * Przykład:
     * "iPhone-15" -> "iphone 15"
     * "IPHONE 15" -> "iphone 15"
     */
    private final TextNormalizer normalizer;

    public PrefixMatchScorer(TextNormalizer normalizer) {
        this.normalizer = normalizer;
    }

    /**
     * Zwraca score dopasowania query do sugestii.
     *
     * Skala:
     * - 1.00: displayText zaczyna się od query,
     * - 0.92: alias zaczyna się od query,
     * - 0.75: dowolny token w displayText zaczyna się od query,
     * - 0.00: brak dopasowania.
     *
     * Przykłady:
     *
     * query = "iph"
     * displayText = "iPhone 15"
     * score = 1.0
     *
     * query = "ps"
     * displayText = "Sony PlayStation 5"
     * alias = "ps5"
     * score = 0.92
     *
     * query = "play"
     * displayText = "Sony PlayStation 5"
     * score = 0.75
     */
    public double score(String q, Suggestion s) {

        /*
         * Brak query oznacza brak sensownego dopasowania.
         *
         * Zwracamy 0.0, żeby taka sugestia nie dostała boosta w rankingu.
         */
        if (q == null || q.isBlank()) {
            return 0.0;
        }

        /*
         * Normalizujemy displayText sugestii.
         *
         * Zakładamy, że q jest już znormalizowane wcześniej
         * w AutocompleteService jako ctx.normalizedQuery().
         */
        String d = normalizer.normalize(s.displayText());

        /*
         * Najsilniejsze dopasowanie:
         * displayText zaczyna się od query.
         *
         * Przykład:
         * q = "iph"
         * d = "iphone 15"
         */
        if (d.startsWith(q)) {
            return 1.0;
        }

        /*
         * Drugie najlepsze dopasowanie:
         * któryś alias zaczyna się od query.
         *
         * Alias to alternatywny zapis tej samej sugestii.
         *
         * Przykład:
         * displayText = "Sony PlayStation 5"
         * alias = "ps5"
         * q = "ps"
         */
        for (String a : s.aliases()) {
            if (normalizer.normalize(a).startsWith(q)) {
                return .92;
            }
        }

        /*
         * Słabsze, ale nadal użyteczne dopasowanie:
         * któryś token displayText zaczyna się od query.
         *
         * Przykład:
         * q = "play"
         * displayText = "Sony PlayStation 5"
         *
         * Cały displayText nie zaczyna się od "play",
         * ale token "playstation" już tak.
         */
        if (Arrays.stream(d.split(" "))
                .anyMatch(t -> t.startsWith(q))) {
            return .75;
        }

        /*
         * Brak sensownego dopasowania prefixowego.
         *
         * Sugestia może nadal istnieć jako kandydat z indeksu,
         * ale nie dostaje boosta za jakość dopasowania tekstowego.
         */
        return 0.0;
    }
}