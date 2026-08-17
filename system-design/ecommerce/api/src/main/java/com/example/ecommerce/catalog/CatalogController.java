package com.example.ecommerce.catalog;

import com.example.ecommerce.catalog.dto.CatalogDtos;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * REST controller odpowiedzialny za publiczne API katalogu produktów.
 *
 * Ta klasa wystawia endpointy używane przez frontend do:
 * - pobrania kategorii,
 * - pobrania listy aktywnych produktów,
 * - pobrania szczegółów produktu po slugu,
 * - wyszukiwania produktów.
 *
 * Controller nie zawiera logiki biznesowej katalogu.
 * Całość deleguje do CatalogService.
 */
@RestController
public class CatalogController {

    /**
     * Serwis katalogu produktów.
     *
     * Odpowiada za właściwą logikę:
     * - pobieranie aktywnych produktów,
     * - filtrowanie produktów,
     * - wyszukiwanie,
     * - mapowanie encji na DTO,
     * - ewentualne użycie cache albo OpenSearch.
     */
    private final CatalogService catalog;

    /**
     * Constructor injection.
     *
     * Dzięki temu zależność od CatalogService jest jawna,
     * niemutowalna i łatwa do podstawienia w testach.
     */
    public CatalogController(CatalogService catalog) {
        this.catalog = catalog;
    }

    /**
     * Zwraca listę kategorii produktów.
     *
     * Endpoint publiczny — nie wymaga zalogowanego użytkownika.
     *
     * W aplikacji e-commerce ten endpoint jest używany np. do:
     * - menu kategorii,
     * - filtrów na listingu produktów,
     * - budowania nawigacji sklepu.
     *
     * GET /api/categories
     */
    @GetMapping("/api/categories")
    public List<CatalogDtos.CategoryResponse> categories() {
        return catalog.categories();
    }

    /**
     * Zwraca listę aktywnych produktów.
     *
     * Endpoint publiczny.
     *
     * Kluczowe:
     * - nie powinien zwracać produktów DRAFT/ARCHIVED,
     * - powinien zwracać tylko produkty widoczne dla klienta,
     * - w większym systemie zwykle byłby stronicowany.
     *
     * W tej wersji controller deleguje do catalog.activeProducts(),
     * gdzie powinna znajdować się logika filtrowania aktywnych produktów.
     *
     * GET /api/products
     */
    @GetMapping("/api/products")
    public List<CatalogDtos.ProductResponse> products() {
        return catalog.activeProducts();
    }

    /**
     * Zwraca szczegóły produktu po slugu.
     *
     * Slug to czytelny identyfikator produktu używany w URL-u,
     * np. /api/products/wireless-headphones.
     *
     * W e-commerce slug jest lepszy dla SEO i UX niż techniczne ID.
     *
     * CatalogService powinien:
     * - znaleźć produkt po slugu,
     * - upewnić się, że produkt jest aktywny,
     * - zwrócić dane produktu, wariantów, ceny i dostępności,
     * - rzucić 404, jeśli produkt nie istnieje lub nie jest publiczny.
     *
     * GET /api/products/{slug}
     */
    @GetMapping("/api/products/{slug}")
    public CatalogDtos.ProductResponse product(@PathVariable String slug) {
        return catalog.bySlug(slug);
    }

    /**
     * Wyszukuje produkty po zapytaniu tekstowym.
     *
     * Parametr q jest opcjonalny.
     * Jeśli q jest puste lub go nie ma, serwis może zwrócić domyślną listę produktów.
     *
     * W zależności od etapu projektu CatalogService może używać:
     * - prostego wyszukiwania w bazie danych,
     * - cache,
     * - OpenSearch/Elasticsearch,
     * - fallbacku do DB, jeśli search engine jest niedostępny.
     *
     * Endpoint publiczny, używany np. przez:
     * - search bar,
     * - autocomplete,
     * - stronę wyników wyszukiwania.
     *
     * GET /api/search?q=headphones
     */
    @GetMapping("/api/search")
    public List<CatalogDtos.ProductResponse> search(@RequestParam(required = false) String q) {
        return catalog.search(q);
    }
}