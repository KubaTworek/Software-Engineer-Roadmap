package pl.jakubtworek.backend_engineering.stage_1.block_d.search_engine;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("infrastructure")
@Testcontainers
class OpenSearchContainerTest {

    @Container
    static final GenericContainer<?> OPENSEARCH = new GenericContainer<>(
            DockerImageName.parse("opensearchproject/opensearch:3.3.2"))
            .withEnv("discovery.type", "single-node")
            .withEnv("DISABLE_SECURITY_PLUGIN", "true")
            .withEnv("OPENSEARCH_JAVA_OPTS", "-Xms512m -Xmx512m")
            .withExposedPorts(9200)
            .waitingFor(Wait.forHttp("/").forPort(9200).forStatusCode(200));

    private final HttpClient http = HttpClient.newHttpClient();

    @Test
    void realEngineEnforcesExternalVersionAndExecutesAnalyzedSearch() throws Exception {
        assertThat(send("PUT", "/products", """
                {"mappings":{"properties":{"stable_id":{"type":"keyword"},"title":{"type":"text"}}}}
                """).statusCode()).isEqualTo(200);

        assertThat(send("PUT", "/products/_doc/p-1?version=2&version_type=external_gte", """
                {"stable_id":"p-1","title":"Java concurrency handbook"}
                """).statusCode()).isIn(200, 201);

        assertThat(send("PUT", "/products/_doc/p-1?version=1&version_type=external_gte", """
                {"stable_id":"p-1","title":"stale title"}
                """).statusCode()).isEqualTo(409);

        send("POST", "/products/_refresh", "");
        HttpResponse<String> search = send("POST", "/products/_search", """
                {"query":{"match":{"title":"concurrency"}},"sort":[{"_score":"desc"},{"stable_id":"asc"}]}
                """);

        assertThat(search.statusCode()).isEqualTo(200);
        assertThat(search.body()).contains("Java concurrency handbook").doesNotContain("stale title");
    }

    private HttpResponse<String> send(String method, String path, String body) throws Exception {
        HttpRequest.BodyPublisher publisher = body.isBlank()
                ? HttpRequest.BodyPublishers.noBody()
                : HttpRequest.BodyPublishers.ofString(body);
        HttpRequest request = HttpRequest.newBuilder(URI.create(
                        "http://" + OPENSEARCH.getHost() + ":" + OPENSEARCH.getMappedPort(9200) + path))
                .header("Content-Type", "application/json")
                .method(method, publisher)
                .build();
        return http.send(request, HttpResponse.BodyHandlers.ofString());
    }
}
