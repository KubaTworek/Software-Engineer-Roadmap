package com.example.newsfeed.search;

import com.example.newsfeed.post.PostResponse;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Kontroler HTTP odpowiedzialny za wyszukiwanie postów.
 *
 * W kontekście aplikacji News Feed ten kontroler udostępnia endpoint,
 * przez który użytkownik może znaleźć posty pasujące do zapytania tekstowego.
 *
 * To jest tylko warstwa API:
 * - odbiera parametry HTTP,
 * - przekazuje je do SearchService,
 * - zwraca gotowe PostResponse.
 *
 * Kontroler nie powinien sam wykonywać wyszukiwania,
 * filtrowania ani rankingu wyników.
 */
@RestController
@RequestMapping("/api/v1/search")
public class SearchController {

    /**
     * Serwis wyszukiwania.
     *
     * Odpowiada za właściwą logikę szukania postów:
     * - walidację zapytania,
     * - wyszukanie kandydatów,
     * - ograniczenie liczby wyników,
     * - mapowanie postów do PostResponse.
     */
    private final SearchService searchService;

    /**
     * Wstrzyknięcie serwisu wyszukiwania.
     */
    public SearchController(SearchService searchService) {
        this.searchService = searchService;
    }

    /**
     * Wyszukuje posty po frazie tekstowej.
     *
     * Endpoint:
     * GET /api/v1/search/posts?q=java&limit=20
     *
     * Parametry:
     * - q: szukana fraza,
     * - limit: maksymalna liczba wyników.
     *
     * Przykład:
     * GET /api/v1/search/posts?q=kafka&limit=10
     *
     * Zwraca listę PostResponse, czyli posty gotowe do pokazania klientowi.
     */
    @GetMapping("/posts")
    public List<PostResponse> searchPosts(
            @RequestParam String q,
            @RequestParam(defaultValue = "20") int limit
    ) {
        /*
         * Delegujemy wyszukiwanie do SearchService.
         *
         * Dzięki temu kontroler pozostaje cienki i nie zna szczegółów:
         * - czy szukamy lokalnie w bazie,
         * - czy przez OpenSearch,
         * - czy przez inny silnik wyszukiwania.
         */
        return searchService.search(q, limit);
    }
}