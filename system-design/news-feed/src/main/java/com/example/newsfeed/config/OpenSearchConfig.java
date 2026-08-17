package com.example.newsfeed.config;

import org.apache.hc.core5.http.HttpHost;
import org.opensearch.client.json.jackson.JacksonJsonpMapper;
import org.opensearch.client.opensearch.OpenSearchClient;
import org.opensearch.client.transport.OpenSearchTransport;
import org.opensearch.client.transport.httpclient5.ApacheHttpClient5TransportBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.net.URI;

/**
 * Konfiguracja klienta OpenSearch.
 *
 * SearchService i SearchIndexService używają tego klienta do:
 * - indeksowania postów,
 * - usuwania dokumentów z indeksu,
 * - wykonywania zapytań search.
 */
@Configuration
public class OpenSearchConfig {

    @Bean
    public OpenSearchClient openSearchClient(
            @Value("${newsfeed.opensearch.url:http://localhost:9200}") String url
    ) throws Exception {
        URI uri = URI.create(url);

        OpenSearchTransport transport = ApacheHttpClient5TransportBuilder
                .builder(new HttpHost(uri.getScheme(), uri.getHost(), uri.getPort()))
                .setMapper(new JacksonJsonpMapper())
                .build();

        return new OpenSearchClient(transport);
    }
}