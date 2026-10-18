package com.example.newsfeed.infrastructure;

import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.*;
import org.testcontainers.utility.DockerImageName;

import java.net.URI;
import java.net.http.*;
import java.time.Duration;
import static org.assertj.core.api.Assertions.assertThat;

/** Indeks jest odtwarzalną projekcją: upsert i delete muszą być idempotentne. */
@Testcontainers(disabledWithoutDocker = true)
class OpenSearchProjectionInfrastructureIT {
    @Container
    static final GenericContainer<?> SEARCH = new GenericContainer<>(
            DockerImageName.parse("opensearchproject/opensearch:2.17.1"))
            .withEnv("discovery.type", "single-node")
            .withEnv("plugins.security.disabled", "true")
            // OpenSearch 2.x uruchamia instalator demo przed odczytaniem ustawienia
            // wyłączającego plugin security. Dlatego nawet lokalny kontener testowy
            // potrzebuje silnego hasła startowego, chociaż późniejsze wywołania HTTP
            // nie korzystają z uwierzytelnienia.
            .withEnv("OPENSEARCH_INITIAL_ADMIN_PASSWORD", "NewsFeedStage6!Secure42")
            .withEnv("OPENSEARCH_JAVA_OPTS", "-Xms256m -Xmx256m")
            .withExposedPorts(9200)
            .waitingFor(Wait.forHttp("/_cluster/health").forStatusCode(200).withStartupTimeout(Duration.ofMinutes(2)));

    @Test
    void documentCanBeUpsertedDeletedAndRebuilt() throws Exception {
        HttpClient http = HttpClient.newHttpClient();
        String base = "http://" + SEARCH.getHost() + ":" + SEARCH.getMappedPort(9200);
        request(http, "PUT", base + "/posts", "{}");
        String document = "{\"postId\":\"42\",\"content\":\"distributed feeds\"}";
        request(http, "PUT", base + "/posts/_doc/42?refresh=true", document);
        assertThat(request(http, "GET", base + "/posts/_doc/42", null).statusCode()).isEqualTo(200);
        assertThat(request(http, "DELETE", base + "/posts/_doc/42?refresh=true", null).statusCode()).isEqualTo(200);
        assertThat(request(http, "GET", base + "/posts/_doc/42", null).statusCode()).isEqualTo(404);
        request(http, "PUT", base + "/posts/_doc/42?refresh=true", document);
        assertThat(request(http, "GET", base + "/posts/_doc/42", null).body()).contains("distributed feeds");
    }

    private HttpResponse<String> request(HttpClient client, String method, String url, String body) throws Exception {
        HttpRequest.BodyPublisher publisher = body == null ? HttpRequest.BodyPublishers.noBody()
                : HttpRequest.BodyPublishers.ofString(body);
        return client.send(HttpRequest.newBuilder(URI.create(url)).header("Content-Type", "application/json")
                .method(method, publisher).build(), HttpResponse.BodyHandlers.ofString());
    }
}
