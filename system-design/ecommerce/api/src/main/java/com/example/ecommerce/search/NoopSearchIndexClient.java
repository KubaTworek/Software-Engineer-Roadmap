package com.example.ecommerce.search;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * No-op implementacja klienta wyszukiwarki.
 *
 * Ta klasa jest używana wtedy, gdy OpenSearch jest wyłączony.
 *
 * Dzięki niej aplikacja może działać lokalnie albo w MVP bez uruchamiania
 * zewnętrznej wyszukiwarki.
 *
 * To jest implementacja "bez efektu":
 * - indeksowanie produktu nic nie robi,
 * - wyszukiwanie zawsze zwraca pustą listę ID.
 *
 * CatalogService może wtedy wykonać fallback do prostego wyszukiwania po bazie danych.
 */
@Component
@ConditionalOnProperty(
        prefix = "app.opensearch",
        name = "enabled",
        havingValue = "false",
        matchIfMissing = true
)
public class NoopSearchIndexClient implements SearchIndexClient {

    /**
     * Metoda indeksowania produktu.
     *
     * W tej implementacji celowo nic nie robi.
     *
     * Dzięki temu kod typu:
     * searchIndexer.index(product)
     *
     * może być wywoływany niezależnie od tego, czy OpenSearch jest włączony.
     * Aplikacja nie musi mieć ifów typu:
     * if (opensearchEnabled) indexProduct(...)
     */
    public void indexProduct(ProductSearchDocument document) {
    }

    /**
     * Wyszukiwanie produktu w trybie bez OpenSearch.
     *
     * Zwracamy pustą listę ID.
     *
     * To jest sygnał dla CatalogService:
     * search engine nie zwrócił wyników, więc można przejść do fallbacku,
     * np. wyszukiwania po nazwie/opisie w bazie danych.
     */
    public List<Long> searchProductIds(String query) {
        return List.of();
    }
}