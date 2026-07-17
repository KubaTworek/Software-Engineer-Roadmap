package com.example.ecommerce.search;

import com.example.ecommerce.integration.IntegrationRetryService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * Klient integracji z OpenSearch.
 *
 * Ta klasa implementuje SearchIndexClient i odpowiada za techniczną komunikację
 * z OpenSearch przez HTTP.
 *
 * W aplikacji e-commerce OpenSearch służy do szybkiego wyszukiwania produktów,
 * np. po:
 * - nazwie,
 * - opisie,
 * - marce,
 * - kategorii.
 *
 * Ważne:
 * OpenSearch nie jest źródłem prawdy o produkcie.
 * Źródłem prawdy pozostaje baza katalogu.
 *
 * OpenSearch zwraca ID produktów, a CatalogService później doczytuje produkty
 * z bazy danych, żeby zwrócić aktualne i poprawne dane klientowi.
 */
@Component
@ConditionalOnProperty(
        prefix = "app.opensearch",
        name = "enabled",
        havingValue = "true"
)
public class OpenSearchRestIndexClient implements SearchIndexClient {

    /**
     * Konfiguracja OpenSearch.
     *
     * Zawiera m.in.:
     * - baseUrl OpenSearch,
     * - nazwę indeksu produktów,
     * - opcjonalne username/password.
     */
    private final OpenSearchProperties properties;

    /**
     * Klient HTTP Springa.
     *
     * Używany do wykonywania requestów do OpenSearch:
     * - PUT dokumentu produktu,
     * - POST zapytania search.
     */
    private final RestClient restClient;

    /**
     * ObjectMapper do parsowania odpowiedzi JSON z OpenSearch.
     *
     * Używany głównie przy searchProductIds(),
     * gdzie trzeba wyciągnąć productId z hits.hits._source.
     */
    private final ObjectMapper objectMapper;

    /**
     * Wspólny serwis retry dla integracji zewnętrznych.
     *
     * OpenSearch może chwilowo nie odpowiadać albo zwrócić błąd techniczny.
     * Retry pomaga obsłużyć krótkotrwałe problemy bez rozsypywania flow aplikacji.
     */
    private final IntegrationRetryService retry;

    /**
     * Buduje klienta REST do OpenSearch.
     *
     * Jeśli skonfigurowano username, dodawany jest Basic Auth.
     *
     * Dzięki @ConditionalOnProperty ten bean powstaje tylko wtedy,
     * gdy app.opensearch.enabled=true.
     *
     * Gdy OpenSearch jest wyłączony, aplikacja może używać alternatywnego klienta,
     * np. NoopSearchIndexClient.
     */
    public OpenSearchRestIndexClient(
            OpenSearchProperties properties,
            ObjectMapper objectMapper,
            IntegrationRetryService retry
    ) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.retry = retry;

        RestClient.Builder builder = RestClient.builder()
                .baseUrl(properties.baseUrl());

        /*
         * Opcjonalna autoryzacja Basic Auth.
         *
         * Jeśli username nie jest ustawiony, klient działa bez Authorization header.
         */
        if (properties.username() != null && !properties.username().isBlank()) {
            String basic = properties.username() + ":" + properties.password();

            String encoded = Base64.getEncoder()
                    .encodeToString(basic.getBytes(StandardCharsets.UTF_8));

            builder.defaultHeader(
                    "Authorization",
                    "Basic " + encoded
            );
        }

        this.restClient = builder.build();
    }

    /**
     * Indeksuje dokument produktu w OpenSearch.
     *
     * Endpoint OpenSearch:
     * PUT /{index}/_doc/{id}
     *
     * productId jest używany jako ID dokumentu.
     * Dzięki temu ponowne indeksowanie tego samego produktu nadpisuje poprzednią wersję,
     * zamiast tworzyć duplikat w indeksie.
     *
     * Całość jest opakowana w retry, bo indeksowanie to integracja zewnętrzna.
     */
    public void indexProduct(ProductSearchDocument document) {
        retry.run(
                "opensearch.indexProduct",
                () -> restClient
                        .put()
                        .uri(
                                "/{index}/_doc/{id}",
                                properties.productsIndex(),
                                document.productId()
                        )
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(document)
                        .retrieve()
                        .toBodilessEntity()
        );
    }

    /**
     * Wyszukuje produkty w OpenSearch i zwraca ich ID.
     *
     * Zwracamy tylko List<Long>, a nie całe produkty.
     *
     * Dlaczego?
     * OpenSearch jest indeksem wyszukiwania, a nie źródłem prawdy.
     * Po otrzymaniu ID CatalogService doczytuje produkty z bazy danych
     * i filtruje np. tylko ProductStatus.ACTIVE.
     *
     * Flow:
     * 1. Budujemy zapytanie multi_match.
     * 2. Wysyłamy POST /{index}/_search.
     * 3. Parsujemy JSON response.
     * 4. Wyciągamy productId z każdego hita.
     * 5. Zwracamy listę ID w kolejności trafności z OpenSearch.
     */
    public List<Long> searchProductIds(String query) {
        return retry.call(
                "opensearch.searchProductIds",
                () -> {
                    /*
                     * Zapytanie multi_match szuka tekstu w kilku polach dokumentu.
                     *
                     * name^3 oznacza, że trafienie w nazwie produktu ma większą wagę
                     * niż trafienie w opisie, marce albo kategorii.
                     *
                     * size=50 ogranicza liczbę wyników.
                     */
                    Map<String, Object> body = Map.of(
                            "query",
                            Map.of(
                                    "multi_match",
                                    Map.of(
                                            "query", query,
                                            "fields", List.of(
                                                    "name^3",
                                                    "description",
                                                    "brand",
                                                    "categoryName"
                                            )
                                    )
                            ),
                            "size",
                            50
                    );

                    /*
                     * Wysyłamy zapytanie do indeksu produktów.
                     *
                     * Odpowiedź pobieramy jako String, bo dalej ręcznie wyciągamy
                     * tylko productId z hitsów.
                     */
                    String response = restClient
                            .post()
                            .uri(
                                    "/{index}/_search",
                                    properties.productsIndex()
                            )
                            .contentType(MediaType.APPLICATION_JSON)
                            .body(body)
                            .retrieve()
                            .body(String.class);

                    JsonNode root = objectMapper.readTree(response);

                    List<Long> ids = new ArrayList<>();

                    /*
                     * OpenSearch zwraca wyniki w:
                     * hits.hits
                     *
                     * Każdy hit ma _source, a w nim productId zapisany
                     * podczas indeksowania ProductSearchDocument.
                     */
                    for (JsonNode hit : root.path("hits").path("hits")) {
                        JsonNode productId = hit.path("_source").path("productId");

                        if (productId.canConvertToLong()) {
                            ids.add(productId.asLong());
                        }
                    }

                    return ids;
                }
        );
    }
}