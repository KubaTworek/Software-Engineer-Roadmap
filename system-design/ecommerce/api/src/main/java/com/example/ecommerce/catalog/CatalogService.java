package com.example.ecommerce.catalog;

import com.example.ecommerce.catalog.dto.CatalogDtos;
import com.example.ecommerce.common.ApiException;
import com.example.ecommerce.inventory.InventoryService;
import com.example.ecommerce.outbox.OutboxService;
import com.example.ecommerce.search.ProductSearchIndexer;
import com.example.ecommerce.search.SearchIndexClient;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Serwis domenowy katalogu produktów.
 *
 * Odpowiada za:
 * - pobieranie kategorii,
 * - pobieranie aktywnych produktów,
 * - wyszukiwanie produktów,
 * - pobieranie produktu po slugu,
 * - walidację wariantu produktu dla koszyka/checkoutu,
 * - tworzenie produktu przez admina,
 * - aktualizację cache,
 * - indeksowanie produktu w search engine,
 * - publikację eventów domenowych przez outbox.
 *
 * To jest kluczowy serwis dla ścieżki zakupowej, bo katalog jest używany przez:
 * frontend, koszyk, checkout, search, rekomendacje i admin panel.
 */
@Service
public class CatalogService {

    /**
     * Repozytorium produktów.
     *
     * Źródło prawdy dla danych produktowych:
     * SKU, nazwa, slug, opis, marka, kategoria, status, warianty.
     */
    private final ProductRepository products;

    /**
     * Repozytorium wariantów produktu.
     *
     * Wariant to konkretna wersja produktu kupowana przez klienta,
     * np. rozmiar, kolor, model, konfiguracja.
     *
     * Koszyk i checkout operują zwykle na variantId, nie tylko na productId.
     */
    private final ProductVariantRepository variants;

    /**
     * Repozytorium kategorii.
     *
     * Kategorie są używane w menu, filtrach, listingach,
     * promocjach kategorii i strukturze katalogu.
     */
    private final CategoryRepository categories;

    /**
     * Serwis inventory.
     *
     * Catalog sam nie przechowuje finalnej dostępności produktu.
     * Dostępność wariantu jest pobierana z InventoryService.
     *
     * Dzięki temu dane produktowe i stan magazynowy są rozdzielone.
     */
    private final InventoryService inventory;

    /**
     * Komponent odpowiedzialny za indeksowanie produktu w wyszukiwarce.
     *
     * W Stage 2/3 może to być OpenSearch/Elasticsearch albo mock/fallback.
     * Indeks search nie jest źródłem prawdy — źródłem prawdy pozostaje baza katalogu.
     */
    private final ProductSearchIndexer searchIndexer;

    /**
     * Klient wyszukiwarki.
     *
     * Używany przy search(query). Zwraca ID produktów znalezionych w search engine.
     * Potem produkty i tak są doczytywane z bazy, żeby zachować kontrolę nad statusem
     * i aktualnym modelem odpowiedzi.
     */
    private final SearchIndexClient searchIndexClient;

    /**
     * Serwis outbox.
     *
     * Zapisuje eventy domenowe w tej samej transakcji co operacja biznesowa.
     * Dzięki temu np. ProductCreated nie ginie, jeśli broker/search/worker chwilowo nie działa.
     */
    private final OutboxService outbox;

    /**
     * Constructor injection.
     *
     * Zależności są jawne i finalne.
     *
     * Uwaga praktyczna:
     * W produkcyjnym kodzie warto sformatować konstruktor w wielu liniach,
     * bo ta klasa ma dużo zależności i jest centralnym serwisem katalogu.
     */
    public CatalogService(
            ProductRepository products,
            ProductVariantRepository variants,
            CategoryRepository categories,
            InventoryService inventory,
            ProductSearchIndexer searchIndexer,
            SearchIndexClient searchIndexClient,
            OutboxService outbox
    ) {
        this.products = products;
        this.variants = variants;
        this.categories = categories;
        this.inventory = inventory;
        this.searchIndexer = searchIndexer;
        this.searchIndexClient = searchIndexClient;
        this.outbox = outbox;
    }

    /**
     * Zwraca listę kategorii.
     *
     * @Transactional(readOnly = true):
     * To operacja tylko do odczytu. Daje jasny sygnał dla JPA i ewentualnego
     * routingu read-replica, że można wykonać ją na replice.
     *
     * @Cacheable("categories"):
     * Kategorie zmieniają się rzadko, więc są dobrym kandydatem do cache.
     * Dzięki temu menu kategorii nie uderza za każdym razem do bazy.
     */
    @Transactional(readOnly = true)
    @Cacheable("categories")
    public List<CatalogDtos.CategoryResponse> categories() {
        return categories.findAll()
                .stream()
                .map(this::toCategoryResponse)
                .toList();
    }

    /**
     * Zwraca aktywne produkty widoczne dla klienta.
     *
     * Kluczowe:
     * - pokazujemy tylko ProductStatus.ACTIVE,
     * - nie zwracamy produktów DRAFT/ARCHIVED,
     * - wynik jest cache’owany jako catalogHome.
     *
     * To endpoint typowy dla strony głównej, listingu lub MVP katalogu.
     */
    @Transactional(readOnly = true)
    @Cacheable(value = "catalogHome", key = "'activeProducts'")
    public List<CatalogDtos.ProductResponse> activeProducts() {
        return products.findByStatus(ProductStatus.ACTIVE)
                .stream()
                .map(this::toProductResponse)
                .toList();
    }

    /**
     * Wyszukuje produkty po query.
     *
     * Flow:
     * 1. Jeśli query jest puste, zwracamy aktywne produkty.
     * 2. Najpierw próbujemy search engine przez SearchIndexClient.
     * 3. Search engine zwraca listę productId w kolejności trafności.
     * 4. Produkty doczytujemy z bazy danych.
     * 5. Zachowujemy kolejność z wyszukiwarki przez LinkedHashMap.
     * 6. Odfiltrowujemy produkty nieaktywne.
     * 7. Jeśli search engine nic nie zwrócił, robimy fallback do prostego searcha po DB.
     *
     * @Cacheable:
     * Wyniki searcha są cache’owane per query.
     * To ogranicza koszt popularnych zapytań, np. "iphone", "buty", "laptop".
     *
     * Ważne:
     * Search engine nie jest źródłem prawdy.
     * Dlatego po otrzymaniu ID z searcha i tak pobieramy produkty z bazy.
     */
    @Transactional(readOnly = true)
    @Cacheable(value = "searchResults", key = "#query == null ? '' : #query")
    public List<CatalogDtos.ProductResponse> search(String query) {
        if (query == null || query.isBlank()) {
            return activeProducts();
        }

        /*
         * Próba wyszukania produktów w OpenSearch/Elasticsearch albo innym search engine.
         *
         * Ten klient powinien mieć retry/fallback po swojej stronie,
         * bo search jest ważny, ale nie powinien blokować całego katalogu,
         * jeśli wyszukiwarka chwilowo nie działa.
         */
        List<Long> ids = searchIndexClient.searchProductIds(query);

        if (!ids.isEmpty()) {
            /*
             * LinkedHashMap zachowuje kolejność ID zwróconą przez search engine.
             *
             * To ważne, bo kolejność z searcha zwykle oznacza trafność wyników.
             * Gdyby użyć zwykłej mapy i zwrócić produkty z findAllById bez kontroli kolejności,
             * moglibyśmy stracić ranking wyszukiwarki.
             */
            Map<Long, Product> byId = new LinkedHashMap<>();

            products.findAllById(ids)
                    .forEach(product -> byId.put(product.getId(), product));

            return ids.stream()
                    .map(byId::get)
                    .filter(product -> product != null && product.getStatus() == ProductStatus.ACTIVE)
                    .map(this::toProductResponse)
                    .toList();
        }

        /*
         * Fallback do prostego wyszukiwania po bazie danych.
         *
         * Ten fallback jest przydatny w MVP i w sytuacjach awaryjnych,
         * ale nie zastąpi pełnego search engine dla dużego katalogu.
         */
        return products
                .findByStatusAndNameContainingIgnoreCaseOrStatusAndDescriptionContainingIgnoreCase(
                        ProductStatus.ACTIVE,
                        query,
                        ProductStatus.ACTIVE,
                        query
                )
                .stream()
                .map(this::toProductResponse)
                .toList();
    }

    /**
     * Pobiera szczegóły produktu po slugu.
     *
     * Slug jest publicznym identyfikatorem w URL, np.:
     * /api/products/wireless-headphones
     *
     * Kluczowe:
     * - zwracamy tylko produkt ACTIVE,
     * - produkt DRAFT/ARCHIVED zachowuje się jak nieistniejący dla klienta,
     * - wynik jest cache’owany pod productDetails.
     *
     * Jeśli produkt nie istnieje albo nie jest aktywny, rzucamy 404.
     */
    @Transactional(readOnly = true)
    @Cacheable(value = "productDetails", key = "#slug")
    public CatalogDtos.ProductResponse bySlug(String slug) {
        Product product = products.findBySlugAndStatus(slug, ProductStatus.ACTIVE)
                .orElseThrow(() -> ApiException.notFound("Product not found"));

        return toProductResponse(product);
    }

    /**
     * Zwraca aktywny wariant produktu.
     *
     * Ta metoda jest używana przez koszyk i potencjalnie checkout.
     *
     * Bardzo ważne:
     * użytkownik nie może dodać do koszyka wariantu:
     * - który nie istnieje,
     * - który jest nieaktywny,
     * - którego produkt nadrzędny nie jest ACTIVE.
     *
     * Dzięki temu koszyk nie przyjmuje produktów ukrytych, archiwalnych
     * albo technicznie istniejących w bazie, ale niewidocznych dla klienta.
     */
    public ProductVariant getActiveVariant(Long variantId) {
        ProductVariant variant = variants.findById(variantId)
                .orElseThrow(() -> ApiException.notFound("Product variant not found"));

        if (!variant.isActive() || variant.getProduct().getStatus() != ProductStatus.ACTIVE) {
            throw ApiException.badRequest("Product variant is not active");
        }

        return variant;
    }

    /**
     * Tworzy produkt z jednym wariantem i początkowym stanem magazynowym.
     *
     * To endpoint typowo używany przez Admin API.
     *
     * Flow:
     * 1. Sprawdź, czy kategoria istnieje.
     * 2. Utwórz Product.
     * 3. Utwórz ProductVariant.
     * 4. Powiąż wariant z produktem.
     * 5. Zapisz produkt.
     * 6. Utwórz inventory item dla wariantu.
     * 7. Zindeksuj produkt w wyszukiwarce.
     * 8. Zapisz event ProductCreated w outbox.
     * 9. Wyczyść cache katalogu.
     *
     * @CacheEvict:
     * Po utworzeniu produktu czyścimy cache katalogu, searcha i szczegółów,
     * żeby frontend nie widział starych danych.
     *
     * Ważne:
     * Outbox event jest potrzebny dla usług pobocznych, np. search-indexer,
     * rekomendacji, integracji ERP albo analityki.
     */
    @Transactional
    @CacheEvict(
            value = {
                    "products",
                    "productBySlug",
                    "searchResults",
                    "catalogHome",
                    "productDetails",
                    "categoryTree"
            },
            allEntries = true
    )
    public CatalogDtos.ProductResponse createProduct(CatalogDtos.CreateProductRequest request) {
        Category category = categories.findById(request.categoryId())
                .orElseThrow(() -> ApiException.notFound("Category not found"));

        Product product = new Product(
                request.sku(),
                request.name(),
                request.slug(),
                request.description(),
                request.brand(),
                category
        );

        ProductVariant variant = new ProductVariant(
                request.variantSku(),
                request.variantName(),
                request.price(),
                request.currency()
        );

        /*
         * addVariant ustawia relację po obu stronach:
         * product -> variant oraz variant -> product.
         *
         * To ważne dla JPA, żeby cascade i relacja działały poprawnie.
         */
        product.addVariant(variant);

        products.save(product);

        /*
         * Po utworzeniu wariantu tworzymy jego stan magazynowy.
         *
         * Catalog zna produkt i wariant, ale nie powinien samodzielnie
         * zarządzać stockiem. Od tego jest InventoryService.
         */
        inventory.createInventoryItem(variant, request.initialStock());

        /*
         * Indeksujemy produkt w search engine.
         *
         * Uwaga:
         * W pełnej produkcyjnej architekturze lepiej robić to asynchronicznie
         * przez outbox/event, żeby problem z OpenSearch nie blokował tworzenia produktu.
         * Tutaj mamy bezpośrednie indeksowanie jako praktyczne MVP/Stage 2.
         */
        searchIndexer.index(product);

        /*
         * Event domenowy zapisany przez Outbox Pattern.
         *
         * Dzięki temu inne procesy mogą później zareagować na utworzenie produktu,
         * np. search-indexer, analytics, ERP, recommendation engine.
         */
        outbox.saveEvent(
                "Product",
                product.getId().toString(),
                "ProductCreated",
                Map.of(
                        "productId", product.getId(),
                        "slug", product.getSlug()
                )
        );

        return toProductResponse(product);
    }

    /**
     * Mapuje encję Product na DTO zwracane przez API.
     *
     * Odpowiedź zawiera:
     * - dane produktu,
     * - kategorię,
     * - status,
     * - warianty,
     * - dostępność wariantów z InventoryService.
     *
     * To jest warstwa ochronna między encją JPA a API.
     * Nie zwracamy encji bezpośrednio na zewnątrz.
     */
    public CatalogDtos.ProductResponse toProductResponse(Product product) {
        return new CatalogDtos.ProductResponse(
                product.getId(),
                product.getSku(),
                product.getName(),
                product.getSlug(),
                product.getDescription(),
                product.getBrand(),
                toCategoryResponse(product.getCategory()),
                product.getStatus(),
                product.getVariants()
                        .stream()
                        .map(this::toVariantResponse)
                        .toList()
        );
    }

    /**
     * Mapuje kategorię na DTO.
     *
     * Zwracamy tylko dane potrzebne frontendowi:
     * ID, parentId, nazwę i slug.
     */
    private CatalogDtos.CategoryResponse toCategoryResponse(Category category) {
        return new CatalogDtos.CategoryResponse(
                category.getId(),
                category.getParentId(),
                category.getName(),
                category.getSlug()
        );
    }

    /**
     * Mapuje wariant produktu na DTO.
     *
     * Kluczowy element:
     * dostępność wariantu nie pochodzi z ProductVariant,
     * tylko z InventoryService.
     *
     * Dzięki temu katalog może pokazywać aktualny stock bez mieszania
     * odpowiedzialności katalogu i magazynu.
     */
    private CatalogDtos.ProductVariantResponse toVariantResponse(ProductVariant variant) {
        return new CatalogDtos.ProductVariantResponse(
                variant.getId(),
                variant.getSku(),
                variant.getName(),
                variant.getPrice(),
                variant.getCurrency(),
                variant.isActive(),
                inventory.availableQuantity(variant.getId())
        );
    }
}