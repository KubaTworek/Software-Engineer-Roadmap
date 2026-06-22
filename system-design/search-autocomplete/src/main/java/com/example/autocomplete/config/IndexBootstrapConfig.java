package com.example.autocomplete.config;

import com.example.autocomplete.index.SuggestionTrieIndex;
import com.example.autocomplete.model.Suggestion;
import com.example.autocomplete.rollout.IndexRegistry;
import com.example.autocomplete.service.CanonicalKeyGenerator;
import com.example.autocomplete.service.TextNormalizer;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * Konfiguracja startowa indeksów autocomplete.
 *
 * Ta klasa uruchamia się przy starcie aplikacji i przygotowuje indeksy,
 * zanim endpoint /autocomplete zacznie obsługiwać ruch.
 *
 * Jej rola:
 * - zbudować indeks Trie z listy sugestii,
 * - zarejestrować kilka wersji indeksu w IndexRegistry,
 * - ustawić aktywną wersję indeksu.
 *
 * Dzięki temu AutocompleteService może później pobierać aktywny indeks
 * przez registry.active(), bez znajomości szczegółów jego budowy.
 */
@Configuration
public class IndexBootstrapConfig {

    /**
     * Bean uruchamiany automatycznie przez Spring Boot po starcie aplikacji.
     *
     * CommandLineRunner jest dobrym miejscem na inicjalizację danych demo,
     * np. zbudowanie indeksu w pamięci.
     *
     * W produkcji budowa indeksu często działałaby poza aplikacją,
     * np. jako batch job, pipeline albo osobny index builder.
     */
    @Bean
    CommandLineRunner bootstrapIndexes(
            IndexRegistry registry,
            List<Suggestion> suggestions,
            TextNormalizer normalizer,
            CanonicalKeyGenerator keyGenerator
    ) {
        return args -> {

            /*
             * Budujemy i rejestrujemy pierwszą wersję indeksu.
             *
             * index-v1:
             * - podstawowa wersja produkcyjna,
             * - max 200 kandydatów trzymanych w każdym węźle Trie.
             *
             * Większy maxCandidatesPerNode daje rankerowi więcej kandydatów,
             * ale zwiększa zużycie pamięci.
             */
            registry.register(
                    new SuggestionTrieIndex(
                            "index-v1",
                            suggestions,
                            normalizer,
                            keyGenerator,
                            200
                    )
            );

            /*
             * Budujemy i rejestrujemy drugą wersję indeksu.
             *
             * index-v2-canary:
             * - przykładowa wersja canary,
             * - ma inny parametr maxCandidatesPerNode = 250.
             *
             * Taka wersja może być użyta do testów jakości/latency
             * przed przełączeniem całego ruchu.
             */
            registry.register(
                    new SuggestionTrieIndex(
                            "index-v2-canary",
                            suggestions,
                            normalizer,
                            keyGenerator,
                            250
                    )
            );

            /*
             * Ustawiamy aktywną wersję indeksu.
             *
             * Od tego momentu AutocompleteService będzie używał index-v1
             * przez registry.active().
             *
             * Drugi indeks jest zarejestrowany, ale nie obsługuje ruchu,
             * dopóki ktoś nie wywoła activate("index-v2-canary").
             */
            registry.activate("index-v1");
        };
    }
}