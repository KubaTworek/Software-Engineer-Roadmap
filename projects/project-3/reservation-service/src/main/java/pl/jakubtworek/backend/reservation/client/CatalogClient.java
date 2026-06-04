package pl.jakubtworek.backend.reservation.client;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.UUID;

@Component
public class CatalogClient {
    private final WebClient webClient;
    private final String catalogUrl;

    public CatalogClient(WebClient.Builder builder, @Value("${services.catalog.url}") String catalogUrl) {
        this.webClient = builder.build();
        this.catalogUrl = catalogUrl;
    }

    public AvailabilityResponse getAvailability(UUID eventId) {
        return webClient.get()
                .uri(catalogUrl + "/events/{id}/availability", eventId)
                .retrieve()
                .bodyToMono(AvailabilityResponse.class)
                .block();
    }

    public record AvailabilityResponse(UUID eventId, int availableTickets) {
    }
}
