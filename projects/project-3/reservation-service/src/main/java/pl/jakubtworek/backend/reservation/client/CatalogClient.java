package pl.jakubtworek.backend.reservation.client;

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;
import java.util.UUID;

/**
 * Klient HTTP do Catalog Service używany przez Reservation Service.
 *
 * Reservation Service potrzebuje Catalog Service, żeby sprawdzić dostępność biletów
 * przed utworzeniem rezerwacji.
 *
 * To jest synchroniczna zależność service-to-service:
 *
 * reservation-service
 *   -> catalog-service
 *
 * Dlatego klient ma mechanizmy resilience:
 *
 * - timeout,
 * - retry/backoff,
 * - circuit breaker,
 * - fallback.
 *
 * Ważna decyzja:
 * Jeśli Catalog Service jest niedostępny, nie tworzymy rezerwacji "w ciemno".
 * Bez potwierdzenia availability bezpieczniej jest odrzucić request niż ryzykować
 * overselling.
 */
@Component
public class CatalogClient {

    /**
     * WebClient do wykonywania requestów HTTP.
     *
     * Builder powinien być globalnie skonfigurowany przez common module, np.:
     *
     * - OutboundHttpClientConfig dla timeoutów,
     * - CorrelationWebClientConfig dla propagacji correlationId/requestId,
     * - konfigurację tracingu.
     */
    private final WebClient webClient;

    /**
     * Bazowy URL Catalog Service.
     *
     * W Docker Compose zwykle:
     *
     * http://catalog-service:8081
     *
     * Lokalnie bez Dockera:
     *
     * http://localhost:8081
     */
    private final String catalogUrl;

    public CatalogClient(WebClient.Builder builder, @Value("${services.catalog.url}") String catalogUrl) {
        this.webClient = builder.build();
        this.catalogUrl = catalogUrl;
    }

    /**
     * Pobiera dostępność biletów dla wydarzenia.
     *
     * Retry:
     * Ponawia request zgodnie z konfiguracją resilience4j dla instancji "catalog".
     * Ma to sens przy błędach przejściowych, np. chwilowym timeoucie.
     *
     * CircuitBreaker:
     * Jeśli Catalog Service zacznie regularnie zawodzić, circuit breaker przejdzie w stan OPEN.
     * Wtedy kolejne wywołania będą szybko odrzucane bez obciążania Catalog Service.
     *
     * Fallback:
     * Jeśli retry/circuit breaker nie pozwolą uzyskać odpowiedzi, wywołany zostanie
     * availabilityFallback(...).
     */
    @Retry(name = "catalog")
    @CircuitBreaker(name = "catalog", fallbackMethod = "availabilityFallback")
    public AvailabilityResponse getAvailability(UUID eventId) {
        return webClient.get()
                .uri(catalogUrl + "/events/{id}/availability", eventId)

                /*
                 * retrieve() traktuje odpowiedzi 4xx/5xx jako błędy WebClienta.
                 * Dzięki temu awarie downstreamu mogą zostać obsłużone przez retry/circuit breaker.
                 */
                .retrieve()

                /*
                 * Deserializujemy JSON z Catalog Service do lokalnego DTO klienta.
                 */
                .bodyToMono(AvailabilityResponse.class)

                /*
                 * Timeout na poziomie konkretnego wywołania.
                 *
                 * Reservation Service nie powinien czekać bez końca na Catalog Service.
                 * Jeśli availability nie przyjdzie w rozsądnym czasie, rezerwacja zostanie
                 * bezpiecznie odrzucona.
                 */
                .timeout(Duration.ofSeconds(2))

                /*
                 * ReservationService działa synchronicznie, więc blokujemy na wyniku Mono.
                 *
                 * WebClient jest reaktywny, ale .block() zamienia to wywołanie w klasyczny
                 * synchroniczny call HTTP. W tym projekcie jest to świadomy kompromis.
                 */
                .block();
    }

    /**
     * Fallback dla niedostępnego Catalog Service.
     *
     * Nie zwracamy sztucznej dostępności, np. availableTickets = 999.
     * To byłoby niebezpieczne, bo Reservation Service mógłby utworzyć rezerwację,
     * mimo że realny stan inventory jest nieznany.
     *
     * Zamiast tego rzucamy wyjątek i bezpiecznie przerywamy tworzenie rezerwacji.
     */
    private AvailabilityResponse availabilityFallback(UUID eventId, Throwable exception) {
        throw new IllegalStateException(
                "Catalog availability is temporarily unavailable. Reservation degraded safely.",
                exception
        );
    }

    /**
     * Lokalny DTO odpowiedzi z Catalog Service.
     *
     * To nie jest encja Catalog Service ani model bazy danych.
     * To minimalny kontrakt HTTP potrzebny Reservation Service do podjęcia decyzji,
     * czy można utworzyć rezerwację.
     */
    public record AvailabilityResponse(UUID eventId, int availableTickets) {
    }
}