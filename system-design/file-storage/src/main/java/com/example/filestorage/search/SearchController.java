package com.example.filestorage.search;

import com.example.filestorage.config.CurrentUser;
import org.springframework.web.bind.annotation.*;

/**
 * REST controller odpowiedzialny za wyszukiwanie plików i folderów.
 *
 * Controller nie przeszukuje bazy samodzielnie.
 * Jego zadaniem jest:
 * - odebrać query string z requestu,
 * - pobrać aktualnie zalogowanego użytkownika,
 * - przekazać parametry do SearchService,
 * - zwrócić wyniki wyszukiwania.
 *
 * Logika typu:
 * - po jakich polach szukamy,
 * - czy użytkownik ma dostęp do wyniku,
 * - jak działa paginacja,
 * - czy query jest poprawne,
 * powinna znajdować się w SearchService.
 */
@RestController
@RequestMapping("/api/v1/search")
public class SearchController {

    /**
     * Serwis biznesowy wyszukiwania.
     *
     * SearchController tylko deleguje request.
     * SearchService powinien korzystać z indeksu wyszukiwania,
     * a nie bezpośrednio przeszukiwać wiele tabel w controllerze.
     */
    private final SearchService searchService;

    public SearchController(SearchService searchService) {
        this.searchService = searchService;
    }

    /**
     * Wyszukuje pliki i foldery dostępne dla aktualnego użytkownika.
     *
     * Endpoint:
     * GET /api/v1/search?q=invoice&page=0&size=20
     *
     * currentUser:
     * użytkownik wyciągnięty z kontekstu autoryzacji.
     * Jego ID jest potrzebne, żeby SearchService zwrócił tylko te zasoby,
     * do których użytkownik ma dostęp.
     *
     * query:
     * fraza wpisana przez użytkownika.
     * W praktyce najczęściej służy do szukania po nazwie pliku/folderu
     * oraz podstawowych metadanych.
     *
     * page i size:
     * parametry paginacji.
     * SearchService powinien ograniczyć size do bezpiecznej maksymalnej wartości,
     * np. 100, żeby nie zwracać zbyt dużych odpowiedzi.
     */
    @GetMapping
    public SearchResponse search(CurrentUser currentUser,
                                 @RequestParam("q") String query,
                                 @RequestParam(defaultValue = "0") int page,
                                 @RequestParam(defaultValue = "20") int size) {
        return searchService.search(
                currentUser.id(),
                query,
                page,
                size
        );
    }
}