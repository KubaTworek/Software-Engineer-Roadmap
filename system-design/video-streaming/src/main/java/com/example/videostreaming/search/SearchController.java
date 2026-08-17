package com.example.videostreaming.search;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import static com.example.videostreaming.search.SearchDtos.*;

/**
 * Publiczny kontroler wyszukiwania filmów.
 *
 * Odpowiada tylko za wystawienie endpointu HTTP.
 * Właściwe wyszukiwanie, ranking, cache, fallback do bazy
 * i komunikacja z search engine są ukryte w SearchService.
 */
@RestController
@RequestMapping("/api/search")
public class SearchController {

    /**
     * Serwis realizujący logikę wyszukiwania.
     *
     * Kontroler nie powinien znać szczegółów tego,
     * czy dane pochodzą z OpenSearch, cache Redis,
     * PostgreSQL fallbacku czy innego źródła.
     */
    private final SearchService search;

    public SearchController(SearchService search) {
        this.search = search;
    }

    /**
     * Wyszukuje filmy po frazie użytkownika.
     *
     * Endpoint używany przez search bar w aplikacji.
     *
     * Parametry:
     * - q: fraza wpisana przez użytkownika,
     * - limit: maksymalna liczba wyników, domyślnie 20.
     *
     * Ważne:
     * Kontroler nie filtruje wyników samodzielnie.
     * SearchService powinien zadbać o to, żeby zwrócić tylko treści,
     * które mogą być pokazane użytkownikowi, np. opublikowane i publiczne.
     */
    @GetMapping
    public SearchResponse search(@RequestParam String q,
                                 @RequestParam(defaultValue = "20") int limit) {
        return search.search(q, limit);
    }
}