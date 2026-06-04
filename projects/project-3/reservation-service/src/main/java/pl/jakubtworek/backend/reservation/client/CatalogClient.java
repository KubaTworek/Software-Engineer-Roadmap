package pl.jakubtworek.backend.reservation.client;

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;
import java.util.UUID;

@Component
public class CatalogClient {
    private final WebClient webClient;
    private final String catalogUrl;

    public CatalogClient(WebClient.Builder builder, @Value("${services.catalog.url}") String catalogUrl) {
        this.webClient = builder.build();
        this.catalogUrl = catalogUrl;
    }

    @Retry(name = "catalog")
    @CircuitBreaker(name = "catalog", fallbackMethod = "availabilityFallback")
    public AvailabilityResponse getAvailability(UUID eventId) {
        return webClient.get()
                .uri(catalogUrl + "/events/{id}/availability", eventId)
                .retrieve()
                .bodyToMono(AvailabilityResponse.class)
                .timeout(Duration.ofSeconds(2))
                .block();
    }

    private AvailabilityResponse availabilityFallback(UUID eventId, Throwable exception) {
        throw new IllegalStateException("Catalog availability is temporarily unavailable. Reservation degraded safely.", exception);
    }

    public record AvailabilityResponse(UUID eventId, int availableTickets) {
    }
}
