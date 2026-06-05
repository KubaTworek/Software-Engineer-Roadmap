package pl.jakubtworek.backend.order.client;

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

/**
 * Klient HTTP do reservation-service używany przez Order Service.
 *
 * Order Service potrzebuje Reservation Service w dwóch miejscach:
 *
 * 1. przy tworzeniu zamówienia — żeby pobrać i zweryfikować rezerwację,
 * 2. po udanej płatności — żeby potwierdzić rezerwację.
 *
 * To jest zależność synchroniczna service-to-service, dlatego musi mieć:
 *
 * - timeout,
 * - retry/backoff,
 * - circuit breaker,
 * - fallback.
 *
 * Bez tych zabezpieczeń awaria Reservation Service mogłaby zablokować Order Service.
 */
@Component
public class ReservationClient {

    /**
     * WebClient używany do wykonywania requestów HTTP do reservation-service.
     *
     * Builder pochodzi ze Springa i powinien być wcześniej skonfigurowany globalnie,
     * np. przez OutboundHttpClientConfig oraz CorrelationWebClientConfig.
     *
     * Dzięki temu klient dziedziczy:
     *
     * - timeouty połączeń,
     * - propagację correlationId/requestId,
     * - ewentualne inne filtry WebClienta.
     */
    private final WebClient webClient;

    /**
     * Bazowy URL reservation-service.
     *
     * W Docker Compose zwykle będzie to:
     *
     * http://reservation-service:8082
     *
     * Lokalnie bez Dockera może to być:
     *
     * http://localhost:8082
     */
    private final String reservationUrl;

    public ReservationClient(WebClient.Builder builder, @Value("${services.reservation.url}") String reservationUrl) {
        this.webClient = builder.build();
        this.reservationUrl = reservationUrl;
    }

    /**
     * Pobiera rezerwację po ID.
     *
     * Retry:
     * Ponawia request przy błędach przejściowych, np. chwilowy timeout albo chwilowy błąd 5xx,
     * zgodnie z konfiguracją resilience4j dla instancji "reservation".
     *
     * CircuitBreaker:
     * Jeśli reservation-service zacznie regularnie zawodzić, circuit breaker przejdzie w stan OPEN
     * i kolejne wywołania będą szybko odrzucane bez dalszego obciążania downstreamu.
     *
     * Fallback:
     * Jeśli retry/circuit breaker nie pozwolą uzyskać poprawnej odpowiedzi, wywołany zostanie
     * getFallback(...), który zamienia problem techniczny na kontrolowany wyjątek aplikacyjny.
     */
    @Retry(name = "reservation")
    @CircuitBreaker(name = "reservation", fallbackMethod = "getFallback")
    public ReservationResponse get(UUID reservationId) {
        return webClient.get()
                .uri(reservationUrl + "/reservations/{id}", reservationId)
                .retrieve()

                /*
                 * Deserializujemy body odpowiedzi do lokalnego DTO.
                 *
                 * To DTO jest kontraktem klienta Order Service wobec Reservation Service.
                 */
                .bodyToMono(ReservationResponse.class)

                /*
                 * Dodatkowy timeout na poziomie konkretnego wywołania.
                 *
                 * Jest niezależny od globalnych timeoutów HTTP.
                 * Dzięki temu ten use case ma jasny limit oczekiwania na Reservation Service.
                 */
                .timeout(Duration.ofSeconds(2))

                /*
                 * OrderService jest klasyczną warstwą synchroniczną, więc tutaj blokujemy
                 * na wyniku Mono.
                 *
                 * To jest akceptowalne w tym projekcie, ale trzeba rozumieć trade-off:
                 * WebClient jest reaktywny, natomiast .block() zamienia wywołanie w synchroniczne.
                 */
                .block();
    }

    /**
     * Potwierdza rezerwację po udanej płatności.
     *
     * Ten call jest krytyczny dla finalizacji zamówienia:
     *
     * - płatność się udała,
     * - reservation-service powinien zmienić status rezerwacji na CONFIRMED.
     *
     * Jeśli confirm się nie uda, OrderService przejdzie do graceful degradation
     * i oznaczy zamówienie jako PAYMENT_PENDING.
     */
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

    /**
     * Fallback dla pobierania rezerwacji.
     *
     * Nie zwracamy sztucznej rezerwacji, bo Order Service nie powinien podejmować decyzji
     * o płatności na podstawie zmyślonych danych.
     *
     * Zamiast tego rzucamy IllegalStateException, którą OrderService może potraktować jako
     * dependency failure.
     */
    private ReservationResponse getFallback(UUID reservationId, Throwable exception) {
        throw new IllegalStateException("Reservation service unavailable while reading reservation.", exception);
    }

    /**
     * Fallback dla potwierdzania rezerwacji.
     *
     * Również nie udajemy sukcesu. Jeśli nie udało się potwierdzić rezerwacji,
     * stan zamówienia powinien przejść do kontrolowanej degradacji, np. PAYMENT_PENDING.
     */
    private ReservationResponse confirmFallback(UUID reservationId, Throwable exception) {
        throw new IllegalStateException("Reservation service unavailable while confirming reservation.", exception);
    }

    /**
     * Lokalny model odpowiedzi Reservation Service.
     *
     * To nie jest encja domenowa Order Service, tylko DTO klienta HTTP.
     *
     * Dzięki temu Order Service zależy od kontraktu API Reservation Service,
     * a nie od jego wewnętrznego modelu bazy danych.
     */
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