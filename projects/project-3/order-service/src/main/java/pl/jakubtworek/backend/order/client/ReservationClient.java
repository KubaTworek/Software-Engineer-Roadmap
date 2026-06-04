package pl.jakubtworek.backend.order.client;

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;
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

    @Retry(name = "reservation")
    @CircuitBreaker(name = "reservation", fallbackMethod = "getFallback")
    public ReservationResponse get(UUID reservationId) {
        return webClient.get()
                .uri(reservationUrl + "/reservations/{id}", reservationId)
                .retrieve()
                .bodyToMono(ReservationResponse.class)
                .timeout(Duration.ofSeconds(2))
                .block();
    }

    @Retry(name = "reservation")
    @CircuitBreaker(name = "reservation", fallbackMethod = "confirmFallback")
    public ReservationResponse confirm(UUID reservationId) {
        return webClient.post()
                .uri(reservationUrl + "/reservations/{id}/confirm", reservationId)
                .retrieve()
                .bodyToMono(ReservationResponse.class)
                .timeout(Duration.ofSeconds(2))
                .block();
    }

    private ReservationResponse getFallback(UUID reservationId, Throwable exception) {
        throw new IllegalStateException("Reservation service unavailable while reading reservation.", exception);
    }

    private ReservationResponse confirmFallback(UUID reservationId, Throwable exception) {
        throw new IllegalStateException("Reservation service unavailable while confirming reservation.", exception);
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
