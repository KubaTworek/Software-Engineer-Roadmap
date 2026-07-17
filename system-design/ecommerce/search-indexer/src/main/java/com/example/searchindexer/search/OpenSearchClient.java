package com.example.searchindexer.search;

import com.example.searchindexer.integration.IntegrationRetryService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * Klient techniczny do komunikacji z OpenSearch w aplikacji search-indexer.
 *
 * Ten komponent nie decyduje, kiedy produkt ma zostać zaindeksowany.
 * Od tego jest worker / handler eventów, który odbiera zdarzenia z outboxa.
 *
 * Ta klasa odpowiada tylko za:
 * - zbudowanie klienta HTTP do OpenSearch,
 * - zapis dokumentu produktu do indeksu,
 * - opakowanie wywołania w retry.
 *
 * W architekturze Stage 3/4 search-indexer jest osobną aplikacją,
 * więc indeksowanie produktów nie musi obciążać głównego API.
 */
@Component
public class OpenSearchClient {

    /**
     * Klient HTTP Springa.
     *
     * Używany do wysłania requestu PUT do OpenSearch.
     */
    private final RestClient client;

    /**
     * Nazwa indeksu produktów w OpenSearch.
     *
     * Przykład:
     * products
     * ecommerce-products
     * products-v1
     *
     * Warto trzymać ją w konfiguracji, żeby można było zmieniać indeks
     * bez przebudowywania aplikacji.
     */
    private final String index;

    /**
     * Wspólny serwis retry dla integracji zewnętrznych.
     *
     * OpenSearch może chwilowo nie odpowiadać, być restartowany
     * albo zwrócić błąd sieciowy.
     *
     * Retry zabezpiecza worker przed jednorazowymi błędami technicznymi.
     */
    private final IntegrationRetryService retry;

    /**
     * Tworzy klienta OpenSearch.
     *
     * baseUrl i nazwa indeksu pochodzą z konfiguracji aplikacji:
     * - app.opensearch.base-url
     * - app.opensearch.products-index
     *
     * Dzięki temu ten sam search-indexer może działać lokalnie,
     * na stagingu i na produkcji z inną konfiguracją.
     */
    public OpenSearchClient(
            @Value("${app.opensearch.base-url}") String baseUrl,
            @Value("${app.opensearch.products-index}") String index,
            IntegrationRetryService retry
    ) {
        /*
         * Budujemy RestClient z baseUrl OpenSearch.
         *
         * Przykład baseUrl:
         * http://localhost:9200
         */
        this.client = RestClient.builder()
                .baseUrl(baseUrl)
                .build();

        this.index = index;
        this.retry = retry;
    }

    /**
     * Indeksuje dokument produktu w OpenSearch.
     *
     * Endpoint OpenSearch:
     * PUT /{index}/_doc/{id}
     *
     * Jako ID dokumentu używamy productId.
     *
     * Dlaczego to ważne:
     * - pierwsze wywołanie tworzy dokument,
     * - kolejne wywołanie dla tego samego productId nadpisuje dokument,
     * - nie powstają duplikaty produktu w indeksie.
     *
     * Ta metoda nie zwraca wyniku biznesowego.
     * Jeśli OpenSearch przyjmie dokument, operacja kończy się bez wyjątku.
     */
    public void indexProduct(ProductSearchDocument document) {
        /*
         * Wywołanie OpenSearch opakowane w retry.
         *
         * Nazwa "opensearch.indexProduct" jest użyteczna w logach i metrykach,
         * bo pozwala rozpoznać, która integracja była ponawiana.
         */
        retry.run(
                "opensearch.indexProduct",
                () -> client
                        .put()
                        .uri(
                                "/{index}/_doc/{id}",
                                index,
                                document.productId()
                        )
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(document)
                        .retrieve()
                        .toBodilessEntity()
        );
    }
}