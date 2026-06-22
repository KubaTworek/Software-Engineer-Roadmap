package com.example.autocomplete.trending;

import org.springframework.stereotype.Component;

import java.util.*;

/**
 * Prosty store z trendami dla sugestii autocomplete.
 *
 * Trendy są jednym z sygnałów rankingowych.
 *
 * Dzięki nim sugestia może dostać boost, jeśli jest popularna:
 * - globalnie,
 * - lokalnie w konkretnym kraju.
 *
 * Przykład:
 * "ai laptop deals" może być mocnym trendem w US,
 * a "java spring boot" może być mocniejszy lokalnie w PL.
 *
 * Ta klasa nie rankinguje wyników samodzielnie.
 * Ona tylko zwraca trendingScore dla konkretnej sugestii.
 */
@Component
public class TrendingStore {

    /**
     * Globalne trendy niezależne od kraju.
     *
     * Klucz:
     * - znormalizowany displayText sugestii.
     *
     * Wartość:
     * - score trendu w zakresie 0.0 - 1.0.
     *
     * Przykład:
     * "iphone 15 pro" -> 0.90
     *
     * Oznacza, że ta sugestia jest mocnym globalnym trendem.
     */
    private final Map<String, Double> global = Map.of(
            "iphone 15 pro", .90,
            "sony playstation 5", .80,
            "java spring boot", .60,
            "ai laptop deals", .98
    );

    /**
     * Lokalne trendy per kraj.
     *
     * Pierwszy klucz:
     * - country, np. "PL" albo "US".
     *
     * Drugi klucz:
     * - znormalizowany displayText sugestii.
     *
     * Wartość:
     * - lokalny score trendu.
     *
     * Dzięki temu ta sama sugestia może mieć różną siłę trendu
     * w różnych krajach.
     */
    private final Map<String, Map<String, Double>> local = Map.of(
            "PL", Map.of(
                    "iphone 15 pro", .95,
                    "java spring boot", .80
            ),
            "US", Map.of(
                    "sony playstation 5", .92,
                    "ai laptop deals", .99
            )
    );

    /**
     * Zwraca score trendu dla sugestii.
     *
     * Bierze maksimum z:
     * - trendu globalnego,
     * - trendu lokalnego dla kraju użytkownika.
     *
     * Dzięki temu lokalny trend może podbić sugestię ponad globalny wynik,
     * ale jeśli lokalnego trendu nie ma, nadal można użyć globalnego.
     *
     * Przykład:
     * normalizedDisplayText = "iphone 15 pro"
     * country = "PL"
     *
     * global = 0.90
     * local PL = 0.95
     * wynik = 0.95
     */
    public double trendingScore(String normalizedDisplayText, String country) {

        /*
         * Pobieramy globalny score.
         *
         * Jeśli sugestia nie występuje w globalnych trendach,
         * dostaje 0.0.
         */
        double globalScore = global.getOrDefault(normalizedDisplayText, 0.0);

        /*
         * Pobieramy lokalny score dla kraju.
         *
         * Jeśli:
         * - country jest nieznany,
         * - kraj nie ma mapy trendów,
         * - sugestia nie występuje w trendach tego kraju,
         *
         * wtedy lokalny score wynosi 0.0.
         */
        double localScore = local
                .getOrDefault(country, Map.of())
                .getOrDefault(normalizedDisplayText, 0.0);

        /*
         * Używamy silniejszego sygnału.
         *
         * Jeśli coś trenduje lokalnie mocniej niż globalnie,
         * powinno dostać lokalny boost.
         */
        return Math.max(globalScore, localScore);
    }
}