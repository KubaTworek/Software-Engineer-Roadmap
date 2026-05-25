package pl.jakubtworek.backend_engineering.stage_3.block_c.src.main.java.pl.jakubtworek.cloudarchitecture.service;

import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import java.time.Duration;

/**
 * HTTP client wrapper with an explicit timeout concept.
 *
 * External calls should never wait forever because blocked requests consume
 * container resources and may increase latency and cost.
 */
@Component
public class ExternalApiClient {
    private final RestClient restClient;

    public ExternalApiClient(RestClient.Builder builder) {
        this.restClient = builder.baseUrl("https://api.example.com").build();
    }

    /**
     * Conceptual external call.
     *
     * In production, configure request timeouts on the underlying HTTP client.
     */
    public String fetchData() {
        return restClient.get().uri("/data").retrieve().body(String.class);
    }

    public Duration recommendedTimeout() {
        return Duration.ofSeconds(3);
    }
}
