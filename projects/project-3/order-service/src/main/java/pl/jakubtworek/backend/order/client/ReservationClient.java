package pl.jakubtworek.backend.order.client;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Instant;
import java.util.UUID;

@Component
public class ReservationClient {
    private final WebClient webClient;
    private final String reservationUrl;

    public ReservationClient(WebClient.Builder builder, @Value("${services.reservation.url}") String reservationUrl) {
        this.webClient = builder.build();
        this.reservationUrl = reservationUrl;
    }

    public ReservationResponse get(UUID reservationId) {
        return webClient.get()
                .uri(reservationUrl + "/reservations/{id}", reservationId)
                .retrieve()
                .bodyToMono(ReservationResponse.class)
                .block();
    }

    public ReservationResponse confirm(UUID reservationId) {
        return webClient.post()
                .uri(reservationUrl + "/reservations/{id}/confirm", reservationId)
                .retrieve()
                .bodyToMono(ReservationResponse.class)
                .block();
    }

    public record ReservationResponse(
            UUID id,
            UUID eventId,
            String userId,
            int quantity,
            String status,
            Instant expiresAt,
            Instant createdAt,
            Instant updatedAt
    ) {
    }
}
