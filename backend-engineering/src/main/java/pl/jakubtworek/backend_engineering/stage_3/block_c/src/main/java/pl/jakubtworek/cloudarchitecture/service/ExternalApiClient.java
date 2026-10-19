package pl.jakubtworek.cloudarchitecture.service;

import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
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
    private final Duration timeout;

    public ExternalApiClient(
            RestClient.Builder builder,
            @Value("${external-api.base-url:https://api.example.com}") String baseUrl,
            @Value("${external-api.timeout:3s}") Duration timeout
    ) {
        if (baseUrl == null || baseUrl.isBlank()) {
            throw new IllegalArgumentException("baseUrl must not be blank");
        }
        if (timeout == null || timeout.isZero() || timeout.isNegative()) {
            throw new IllegalArgumentException("timeout must be positive");
        }
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(timeout);
        requestFactory.setReadTimeout(timeout);
        this.restClient = builder
                .baseUrl(baseUrl)
                .requestFactory(requestFactory)
                .build();
        this.timeout = timeout;
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
        return timeout;
    }
}
