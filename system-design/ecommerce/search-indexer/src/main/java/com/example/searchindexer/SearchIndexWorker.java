package com.example.searchindexer;

import com.example.searchindexer.catalog.InventoryItemRepository;
import com.example.searchindexer.catalog.Product;
import com.example.searchindexer.catalog.ProductRepository;
import com.example.searchindexer.outbox.OutboxEventRepository;
import com.example.searchindexer.outbox.ProcessedSearchEvent;
import com.example.searchindexer.outbox.ProcessedSearchEventRepository;
import com.example.searchindexer.search.OpenSearchClient;
import com.example.searchindexer.search.ProductSearchDocument;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Worker odpowiedzialny za asynchroniczne indeksowanie produktów do OpenSearch.
 *
 * Ta klasa należy do osobnej aplikacji search-indexer.
 * Jej zadaniem jest czytanie eventów zapisanych przez główne API w tabeli outbox
 * i aktualizowanie dokumentów produktów w indeksie wyszukiwania.
 *
 * Dzięki temu:
 * - główne API nie musi bezpośrednio blokować requestu na OpenSearch,
 * - awaria OpenSearch nie rozwala tworzenia/aktualizacji produktu,
 * - indeksowanie można skalować osobno od API,
 * - search index jest aktualizowany asynchronicznie.
 *
 * Ważne:
 * OpenSearch nie jest source of truth.
 * Source of truth to baza katalogu i inventory.
 */
@Component
public class SearchIndexWorker {

    private static final Logger log = LoggerFactory.getLogger(SearchIndexWorker.class);

    /**
     * Repozytorium eventów outbox.
     *
     * Worker pobiera z niego eventy, które powinny wpłynąć na search index,
     * np. ProductCreated, ProductUpdated, InventoryUpdated.
     */
    private final OutboxEventRepository outbox;

    /**
     * Repozytorium eventów już przetworzonych przez search-indexer.
     *
     * Zamiast oznaczać event w głównej tabeli outbox jako przetworzony globalnie,
     * ten worker zapisuje osobny rekord ProcessedSearchEvent.
     *
     * Dzięki temu różne konsumenty outboxa mogą mieć własny stan przetwarzania,
     * np. notification-service, ERP sync, search-indexer.
     */
    private final ProcessedSearchEventRepository processed;

    /**
     * Repozytorium produktów.
     *
     * Worker nie bierze pełnych danych produktu z eventu.
     * Event służy tylko jako sygnał, że produkt trzeba przeindeksować.
     *
     * Aktualny stan produktu jest doczytywany z bazy katalogu.
     */
    private final ProductRepository products;

    /**
     * Repozytorium inventory.
     *
     * Dokument search zawiera dostępność wariantów,
     * więc worker musi doczytać sellableQuantity dla każdego wariantu.
     */
    private final InventoryItemRepository inventory;

    /**
     * Klient OpenSearch.
     *
     * Odpowiada za techniczne zapisanie ProductSearchDocument do indeksu.
     */
    private final OpenSearchClient openSearch;

    /**
     * Maksymalna liczba eventów przetwarzanych w jednym cyklu workera.
     *
     * Wartość pochodzi z konfiguracji:
     * app.worker.batch-size
     *
     * Domyślnie: 100.
     */
    private final int batchSize;

    /**
     * Metryka liczby przetworzonych eventów.
     *
     * Może być eksportowana do Prometheusa przez Micrometer.
     */
    private final Counter indexed;

    /**
     * Constructor injection.
     *
     * Worker dostaje repozytoria, klienta OpenSearch, konfigurację batch size
     * oraz MeterRegistry do rejestrowania metryk.
     */
    public SearchIndexWorker(
            OutboxEventRepository outbox,
            ProcessedSearchEventRepository processed,
            ProductRepository products,
            InventoryItemRepository inventory,
            OpenSearchClient openSearch,
            @Value("${app.worker.batch-size:100}") int batchSize,
            MeterRegistry registry
    ) {
        this.outbox = outbox;
        this.processed = processed;
        this.products = products;
        this.inventory = inventory;
        this.openSearch = openSearch;
        this.batchSize = batchSize;
        this.indexed = Counter.builder("search_indexer_events_processed_total")
                .register(registry);
    }

    /**
     * Główna pętla workera.
     *
     * Uruchamia się cyklicznie według konfiguracji:
     * app.worker.fixed-delay-ms
     *
     * Domyślny sens:
     * co kilka sekund worker sprawdza, czy są nowe eventy do indeksowania.
     *
     * @Transactional:
     * pobranie eventów, zapis ProcessedSearchEvent i metryki są wykonywane
     * w ramach jednej transakcji bazy danych.
     *
     * Uwaga:
     * samo wywołanie OpenSearch jest zewnętrznym efektem ubocznym.
     * Nie jest rollbackowalne razem z transakcją DB.
     */
    @Scheduled(fixedDelayString = "${app.worker.fixed-delay-ms:5000}")
    @Transactional
    public void process() {
        /*
         * Pobieramy eventy, które nie zostały jeszcze przetworzone
         * przez search-indexer.
         *
         * limit(batchSize) ogranicza jeden cykl workera,
         * żeby nie próbować przerobić całej zaległości naraz.
         */
        var events = outbox.findUnprocessedSearchEvents()
                .stream()
                .limit(batchSize)
                .toList();

        for (var event : events) {
            /*
             * Event outbox może dotyczyć różnych agregatów.
             *
             * Search-indexer potrzebuje productId.
             * Dlatego najpierw próbujemy rozwiązać productId na podstawie
             * aggregateType i aggregateId.
             */
            Long productId = resolveProductId(
                    event.getAggregateType(),
                    event.getAggregateId()
            );

            /*
             * Jeśli event można powiązać z produktem, doczytujemy aktualny produkt
             * z bazy i indeksujemy go do OpenSearch.
             *
             * Jeśli produkt już nie istnieje, obecna wersja po prostu nic nie robi.
             * Produkcyjnie warto obsłużyć też usuwanie dokumentu z indeksu.
             */
            if (productId != null) {
                products.findById(productId).ifPresent(product -> {
                    openSearch.indexProduct(toDocument(product));

                    log.info(
                            "Indexed product productId={} from outboxEventId={}",
                            product.getId(),
                            event.getId()
                    );
                });
            }

            /*
             * Oznaczamy event jako przetworzony przez search-indexer.
             *
             * Dzięki temu kolejny cykl workera nie będzie ponownie brał
             * tego samego eventu.
             *
             * Uwaga produkcyjna:
             * jeżeli productId == null albo produktu nie znaleziono,
             * event też zostanie oznaczony jako przetworzony.
             * To jest proste zachowanie MVP.
             */
            processed.save(new ProcessedSearchEvent(event.getId()));

            /*
             * Zwiększamy metrykę przetworzonych eventów.
             *
             * Wartość pokazuje throughput search-indexera.
             */
            indexed.increment();
        }
    }

    /**
     * Próbuje ustalić productId na podstawie eventu outbox.
     *
     * Obecna wersja obsługuje tylko eventy, których aggregateType to "Product".
     *
     * Przykład:
     * aggregateType = "Product"
     * aggregateId = "123"
     * wynik = 123L
     *
     * Uwaga:
     * Eventy typu InventoryUpdated mogą wymagać mapowania variantId -> productId.
     * W obecnej wersji nie są jeszcze obsłużone.
     */
    private Long resolveProductId(String aggregateType, String aggregateId) {
        if ("Product".equals(aggregateType)) {
            return Long.valueOf(aggregateId);
        }

        return null;
    }

    /**
     * Buduje dokument search na podstawie aktualnego stanu produktu.
     *
     * Dokument jest denormalizowany.
     * Zawiera:
     * - dane produktu,
     * - dane kategorii,
     * - warianty,
     * - dostępność wariantów,
     * - informację, czy produkt jest ogólnie dostępny.
     *
     * Dzięki temu OpenSearch może szybko wyszukiwać i filtrować produkty
     * bez joinów do tabel katalogu i inventory.
     */
    private ProductSearchDocument toDocument(Product product) {
        /*
         * Mapujemy warianty produktu do dokumentów wariantów.
         *
         * Dla każdego wariantu pobieramy sellableQuantity z inventory.
         *
         * sellableQuantity oznacza ilość dostępną do sprzedaży:
         * availableQuantity - reservedQuantity.
         */
        var variants = product.getVariants()
                .stream()
                .map(variant -> new ProductSearchDocument.VariantDocument(
                        variant.getId(),
                        variant.getSku(),
                        variant.getName(),
                        variant.getPrice(),
                        variant.getCurrency(),
                        inventory.findByVariantId(variant.getId())
                                .map(item -> item.sellableQuantity())
                                .orElse(0)
                ))
                .toList();

        /*
         * Produkt jest dostępny, jeśli przynajmniej jeden wariant
         * ma sellable quantity większe od zera.
         */
        boolean available = variants.stream()
                .anyMatch(variant -> variant.availableQuantity() > 0);

        /*
         * Budujemy dokument produktu dla OpenSearch.
         *
         * Kategoria może być null, więc zabezpieczamy getCategory()
         * przed NullPointerException.
         */
        return new ProductSearchDocument(
                product.getId(),
                product.getSku(),
                product.getName(),
                product.getSlug(),
                product.getDescription(),
                product.getBrand(),
                product.getCategory() == null
                        ? null
                        : product.getCategory().getId(),
                product.getCategory() == null
                        ? null
                        : product.getCategory().getName(),
                variants,
                available
        );
    }
}