package com.example.ecommerce.search;

import com.example.ecommerce.catalog.Product;
import com.example.ecommerce.inventory.InventoryService;
import org.springframework.stereotype.Service;

/**
 * Serwis odpowiedzialny za przygotowanie produktu do indeksowania w search engine.
 *
 * W aplikacji e-commerce wyszukiwarka, np. OpenSearch/Elasticsearch,
 * nie powinna dostawać bezpośrednio encji JPA.
 *
 * Ta klasa mapuje Product na ProductSearchDocument, czyli dokument zoptymalizowany
 * pod wyszukiwanie produktów.
 *
 * Search index może być używany przez:
 * - wyszukiwarkę produktów,
 * - listing produktów,
 * - filtrowanie po kategorii,
 * - sortowanie po dostępności,
 * - autocomplete,
 * - rekomendacje lub merchandising.
 */
@Service
public class ProductSearchIndexer {

    /**
     * Klient indeksu wyszukiwania.
     *
     * Odpowiada za techniczne wysłanie dokumentu do OpenSearch,
     * Elasticsearch albo mock klienta w trybie lokalnym.
     */
    private final SearchIndexClient client;

    /**
     * Serwis inventory.
     *
     * Product sam nie przechowuje aktualnej dostępności.
     * Dostępność wariantów jest pobierana z InventoryService.
     *
     * Dzięki temu dokument search zawiera nie tylko dane katalogowe,
     * ale też informację, czy produkt da się aktualnie kupić.
     */
    private final InventoryService inventory;

    /**
     * Constructor injection.
     *
     * Indexer potrzebuje klienta search engine oraz inventory,
     * bo dokument wyszukiwarki łączy dane katalogu z dostępnością.
     */
    public ProductSearchIndexer(
            SearchIndexClient client,
            InventoryService inventory
    ) {
        this.client = client;
        this.inventory = inventory;
    }

    /**
     * Indeksuje produkt w search engine.
     *
     * Flow:
     * 1. Pobierz warianty produktu.
     * 2. Dla każdego wariantu zbuduj VariantDocument.
     * 3. Dla każdego wariantu pobierz aktualną dostępność z InventoryService.
     * 4. Ustal, czy cały produkt jest dostępny.
     * 5. Zbuduj ProductSearchDocument.
     * 6. Wyślij dokument do SearchIndexClient.
     *
     * Ważne:
     * dokument search jest denormalizowany.
     * Zawiera dane produktu, kategorii, wariantów i availability w jednym obiekcie,
     * żeby wyszukiwarka mogła szybko zwracać wyniki bez joinów.
     */
    public void index(Product product) {
        /*
         * Mapujemy warianty produktu do dokumentów search.
         *
         * Każdy wariant zawiera:
         * - variantId,
         * - SKU,
         * - nazwę wariantu,
         * - cenę,
         * - walutę,
         * - aktualną ilość dostępną do sprzedaży.
         *
         * Dostępność pobieramy z InventoryService, a nie z ProductVariant.
         */
        var variants = product.getVariants()
                .stream()
                .map(variant -> new ProductSearchDocument.VariantDocument(
                        variant.getId(),
                        variant.getSku(),
                        variant.getName(),
                        variant.getPrice(),
                        variant.getCurrency(),
                        inventory.availableQuantity(variant.getId())
                ))
                .toList();

        /*
         * Produkt uznajemy za dostępny, jeśli przynajmniej jeden wariant
         * ma availableQuantity większe od zera.
         *
         * To pozwala search engine filtrować albo boostować produkty dostępne.
         */
        boolean available = variants.stream()
                .anyMatch(variant -> variant.availableQuantity() > 0);

        /*
         * Budujemy dokument wyszukiwarki.
         *
         * Dokument zawiera dane potrzebne do searcha i listingu:
         * - dane produktu,
         * - kategorię,
         * - warianty,
         * - informację o dostępności.
         *
         * Search index nie jest źródłem prawdy.
         * Jest zoptymalizowaną kopią danych do szybkiego wyszukiwania.
         */
        client.indexProduct(
                new ProductSearchDocument(
                        product.getId(),
                        product.getSku(),
                        product.getName(),
                        product.getSlug(),
                        product.getDescription(),
                        product.getBrand(),
                        product.getCategory().getId(),
                        product.getCategory().getName(),
                        variants,
                        available
                )
        );
    }
}