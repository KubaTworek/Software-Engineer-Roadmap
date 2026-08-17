package com.example.autocomplete.index;

import com.example.autocomplete.model.Suggestion;
import com.example.autocomplete.model.SuggestionMetrics;
import com.example.autocomplete.service.TextNormalizer;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Realtime delta index.
 *
 * To jest dodatkowy, mały indeks dla świeżych sugestii.
 *
 * Główny indeks autocomplete jest zwykle budowany batchowo:
 * - co kilka minut,
 * - co godzinę,
 * - raz dziennie,
 * zależnie od skali i wymagań.
 *
 * Problem:
 * nowe trendy albo pilne sugestie nie powinny czekać na pełny rebuild indeksu.
 *
 * Rozwiązanie:
 * trzymamy mały "delta index", który zawiera świeże sugestie
 * i jest odpytywany razem z głównym indeksem.
 *
 * Flow w aplikacji:
 *
 * AutocompleteService:
 * 1. pobiera kandydatów z aktywnego głównego indeksu,
 * 2. dobiera kandydatów z RealtimeDeltaIndex,
 * 3. łączy wyniki,
 * 4. przepuszcza je przez safety filter,
 * 5. rankinguje finalnie.
 */
@Component
public class RealtimeDeltaIndex {

    /**
     * Normalizer zapewnia spójne porównywanie tekstu.
     *
     * Dzięki temu query:
     * - "AI-Laptop"
     * - "ai laptop"
     * - "  ai   laptop "
     *
     * może zostać sprowadzone do tej samej postaci.
     */
    private final TextNormalizer normalizer;

    /**
     * Lista świeżych sugestii.
     *
     * CopyOnWriteArrayList jest thread-safe dla scenariusza:
     * - dużo odczytów,
     * - mało zapisów.
     *
     * To pasuje do delta indexu, bo autocomplete wykonuje bardzo dużo odczytów,
     * a nowe trendy/sugestie są dodawane relatywnie rzadziej.
     *
     * Uwaga:
     * CopyOnWriteArrayList nie nadaje się do bardzo częstych zapisów,
     * bo każdy zapis kopiuje wewnętrzną tablicę.
     */
    private final List<Suggestion> deltaSuggestions = new CopyOnWriteArrayList<>();

    public RealtimeDeltaIndex(TextNormalizer normalizer) {
        this.normalizer = normalizer;

        /*
         * Przykładowa świeża sugestia dodana do delta indexu przy starcie.
         *
         * W produkcji takie sugestie pochodziłyby np. z:
         * - Kafka/Flink,
         * - streamingu trendów,
         * - panelu administracyjnego,
         * - realtime ingestion pipeline.
         */
        deltaSuggestions.add(new Suggestion(
                "delta-ai-laptop",
                "AI Laptop Deals",
                "trending",
                new SuggestionMetrics(
                        300,  // popularity
                        .35,  // CTR
                        .18,  // conversion rate
                        .95,  // freshness/trending score
                        .85   // quality score
                ),
                List.of("ai laptop", "laptop ai"),
                Set.of("electronics"),
                Set.of("en-US"),
                Set.of("US"),
                false
        ));
    }

    /**
     * Zwraca kandydatów z delta indexu dla danego query.
     *
     * Ta metoda nie robi finalnego rankingu.
     * Jej zadanie to tylko candidate generation dla świeżych sugestii.
     *
     * Finalne sortowanie wykonuje później SuggestionRanker.
     */
    public List<Suggestion> candidates(String rawQuery, int limit) {

        /*
         * Normalizujemy query, żeby porównywać tekst w spójnej formie.
         */
        String q = normalizer.normalize(rawQuery);

        /*
         * Szukamy sugestii, których:
         * - displayText zaczyna się od query,
         * - albo któryś alias zaczyna się od query.
         *
         * Przykład:
         * query = "ai"
         *
         * match:
         * - "AI Laptop Deals"
         * - alias "ai laptop"
         */
        return deltaSuggestions.stream()
                .filter(s ->
                        normalizer.normalize(s.displayText()).startsWith(q)
                                || s.aliases()
                                .stream()
                                .anyMatch(a -> normalizer.normalize(a).startsWith(q))
                )
                .limit(limit)
                .toList();
    }

    /**
     * Dodaje nową świeżą sugestię do delta indexu.
     *
     * W produkcyjnym systemie ta metoda mogłaby być wywoływana przez:
     * - consumer eventów,
     * - endpoint adminowy,
     * - pipeline trendów,
     * - moduł realtime ingestion.
     *
     * Dzięki temu nowa sugestia może zacząć działać bez przebudowy
     * głównego indeksu autocomplete.
     */
    public void add(Suggestion suggestion) {
        deltaSuggestions.add(suggestion);
    }

    /**
     * Zwraca liczbę sugestii w delta indexie.
     *
     * Przydatne do:
     * - debugowania,
     * - metryk,
     * - health checków,
     * - sprawdzania, czy delta index nie rośnie bez kontroli.
     */
    public int size() {
        return deltaSuggestions.size();
    }
}