package com.example.autocomplete.cache;

import com.example.autocomplete.model.RankedSuggestion;
import com.github.benmanes.caffeine.cache.*;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.*;

/**
 * Lokalny cache wyników autocomplete.
 *
 * To jest cache typu L1, działający w pamięci jednej instancji aplikacji.
 *
 * Jego cel:
 * - zmniejszyć latency dla powtarzalnych zapytań,
 * - ograniczyć liczbę wejść w indeks i ranking,
 * - odciążyć AutocompleteService przy dużym ruchu,
 * - przyspieszyć gorące prefiksy, np. "ip", "iph", "java".
 *
 * Ważne:
 * ten cache nie jest współdzielony między instancjami aplikacji.
 * Przy wielu replikach każda replika ma własny lokalny cache.
 */
@Component
public class AutocompleteCache {

    /**
     * Cache przechowuje finalnie wyrankingowane sugestie.
     *
     * Klucz: String zbudowany z query i kontekstu requestu.
     * Wartość: lista gotowych RankedSuggestion.
     *
     * maximumSize(50_000):
     * - ogranicza liczbę wpisów w pamięci,
     * - chroni aplikację przed niekontrolowanym wzrostem RAM.
     *
     * expireAfterWrite(45s):
     * - wynik jest ważny przez 45 sekund od zapisu,
     * - po tym czasie zostanie przeliczony ponownie.
     *
     * recordStats():
     * - pozwala zbierać statystyki cache,
     * - np. hit rate, miss rate, eviction count.
     */
    private final Cache<String, List<RankedSuggestion>> cache = Caffeine.newBuilder()
            .maximumSize(50_000)
            .expireAfterWrite(Duration.ofSeconds(45))
            .recordStats()
            .build();

    /**
     * Próbuje pobrać wynik z cache.
     *
     * Jeśli wpis istnieje i nie wygasł, zwracamy Optional z listą sugestii.
     * Jeśli wpisu nie ma albo wygasł, zwracamy Optional.empty().
     *
     * AutocompleteService używa tego jako fast path:
     * - cache hit: zwracamy wynik bez indeksu i rankingu,
     * - cache miss: liczymy wynik normalną ścieżką.
     */
    public Optional<List<RankedSuggestion>> get(String key) {
        return Optional.ofNullable(cache.getIfPresent(key));
    }

    /**
     * Zapisuje gotowy wynik autocomplete do cache.
     *
     * Do cache trafiają już finalne, przefiltrowane i wyrankingowane sugestie.
     * Dzięki temu kolejne identyczne requesty nie muszą ponownie przechodzić przez:
     * - candidate generation,
     * - safety filtering,
     * - ranking.
     */
    public void put(String key, List<RankedSuggestion> value) {
        cache.put(key, value);
    }

    /**
     * Buduje cache key dla requestu autocomplete.
     *
     * To bardzo ważny fragment.
     *
     * Nie można cache'ować tylko po query, np.:
     *
     * "iph"
     *
     * bo wynik autocomplete zależy też od:
     * - użytkownika,
     * - sesji,
     * - języka,
     * - kraju,
     * - kategorii,
     * - wariantu A/B,
     * - wersji indeksu,
     * - limitu wyników.
     *
     * Przykład:
     * ten sam prefix "iph" może dać inny wynik dla:
     * - userId = u-apple,
     * - userId = anonymous,
     * - country = PL,
     * - country = US,
     * - experiment = CONTROL,
     * - experiment = TRENDING_HEAVY.
     *
     * Dlatego wszystkie te elementy muszą być częścią cache key.
     */
    public String key(
            String q,
            String userId,
            String sessionId,
            String locale,
            String country,
            String category,
            String variant,
            String indexVersion,
            int limit
    ) {
        return String.join(
                ":",
                "v6",
                q,
                safe(userId),
                safe(sessionId),
                safe(locale),
                safe(country),
                safe(category),
                variant,
                indexVersion,
                String.valueOf(limit)
        );
    }

    /**
     * Normalizuje puste elementy cache key.
     *
     * Jeśli wartość jest null albo blank, zamieniamy ją na "_".
     *
     * Dzięki temu cache key ma stabilną strukturę i nie dostaje nulli.
     *
     * Przykład:
     * category = null
     *
     * zamiast:
     * v6:iph:u1:s1:en-US:US:null:CONTROL:index-v1:10
     *
     * dostajemy:
     * v6:iph:u1:s1:en-US:US:_:CONTROL:index-v1:10
     */
    private String safe(String value) {
        return value == null || value.isBlank()
                ? "_"
                : value;
    }
}