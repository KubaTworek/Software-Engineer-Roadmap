package pl.jakubtworek.backend.order.client;

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.UUID;
import pl.jakubtworek.backend.common.resilience.AdmissionController;
import pl.jakubtworek.backend.common.web.DeadlineContext;

/**
 * Klient HTTP do payment-mock-service.
 *
 * Order Service używa tego klienta podczas tworzenia zamówienia, żeby wykonać płatność.
 *
 * Payment Service jest celowo traktowany jako zewnętrzna, zawodna zależność:
 *
 * - może odpowiadać wolno,
 * - może zwracać błędy 5xx,
 * - może timeoutować,
 * - może być chwilowo niedostępny.
 *
 * Dlatego ten klient ma mechanizmy resilience:
 *
 * - timeout,
 * - retry/backoff,
 * - circuit breaker,
 * - fallback.
 */
@Component
public class PaymentClient {

    /**
     * WebClient używany do wykonywania requestów HTTP.
     *
     * Builder powinien dziedziczyć globalne ustawienia z modułu common, np.:
     *
     * - timeout połączenia,
     * - response timeout,
     * - propagację correlationId/requestId,
     * - konfigurację tracingu.
     */
    private final WebClient webClient;

    /**
     * Bazowy URL payment-mock-service.
     *
     * W Docker Compose zwykle będzie to:
     *
     * http://payment-mock-service:8084
     *
     * Lokalnie bez Dockera może to być:
     *
     * http://localhost:8084
     */
    private final String paymentUrl;
    private final AdmissionController admissionController;

    public PaymentClient(WebClient.Builder builder,
                         @Value("${services.payment.url}") String paymentUrl,
                         @Value("${app.bulkhead.payment.max-concurrent:32}") int maximumConcurrentCalls) {
        this.webClient = builder.build();
        this.paymentUrl = paymentUrl;
        this.admissionController = new AdmissionController(maximumConcurrentCalls);
    }

    /**
     * Wykonuje płatność dla zamówienia.
     *
     * Retry:
     * Ponawia request zgodnie z konfiguracją resilience4j dla instancji "payment".
     * Ma to sens przy błędach przejściowych, np. chwilowy timeout albo krótkotrwałe 5xx.
     *
     * CircuitBreaker:
     * Jeśli payment-service regularnie zawodzi, circuit breaker przejdzie w stan OPEN.
     * Wtedy kolejne requesty będą szybko odrzucane bez czekania na timeout i bez dalszego
     * przeciążania payment-service.
     *
     * Fallback:
     * Jeśli retry i circuit breaker nie pozwolą uzyskać poprawnej odpowiedzi, wywołany
     * zostanie paymentFallback(...).
     */
    @Retry(name = "payment")
    @CircuitBreaker(name = "payment", fallbackMethod = "paymentFallback")
    public PaymentResponse pay(UUID orderId, String userId, BigDecimal amount) {
        /* Permit ogranicza realną pracę downstream, a nie liczbę utworzonych wątków.
         * Ponieważ znajduje się wewnątrz metody objętej retry, każda próba ponownie
         * konkuruje o limit i nie omija ochrony po pierwszym niepowodzeniu. */
        try (AdmissionController.Permit ignored = admissionController.tryAcquire()) {
            return webClient.post()
                .uri(paymentUrl + "/payments")
                /* Retry każdej próby używa tego samego klucza operacji po stronie providera. */
                .header("Idempotency-Key", "payment:" + orderId)

                /*
                 * Wysyłamy minimalny payload potrzebny do wykonania płatności.
                 *
                 * PaymentRequest jest prywatnym rekordem tej klasy, bo jest to szczegół kontraktu
                 * między Order Service a Payment Service. Nie musi być częścią publicznego API
                 * Order Service.
                 */
                .bodyValue(new PaymentRequest(orderId, userId, amount))

                /*
                 * retrieve() zamienia odpowiedź HTTP na strumień body.
                 *
                 * Domyślnie statusy 4xx/5xx zostaną potraktowane jako błędy WebClienta,
                 * co pozwala uruchomić retry/circuit breaker.
                 */
                .retrieve()

                /*
                 * Oczekujemy odpowiedzi w formacie PaymentResponse.
                 */
                .bodyToMono(PaymentResponse.class)

                /*
                 * Timeout na poziomie konkretnego wywołania płatności.
                 *
                 * To jest ważne, bo payment-service jest zależnością downstream.
                 * Order Service nie powinien wisieć bez końca, czekając na provider płatności.
                 *
                 * Jeśli timeout zostanie przekroczony, wyjątek trafi do mechanizmów
                 * retry/circuit breaker/fallback.
                 */
                .timeout(DeadlineContext.timeoutCappedAt(Duration.ofSeconds(2)))

                /*
                 * OrderService działa synchronicznie, więc tutaj blokujemy na wyniku Mono.
                 *
                 * WebClient jest reaktywny, ale .block() zamienia wywołanie w klasyczny,
                 * synchroniczny call. W tym projekcie jest to świadomy kompromis.
                 */
                    .block();
        }
    }

    /** Lookup jest bezpieczniejszy od ponownego POST, gdy wynik poprzedniej próby jest nieznany. */
    @CircuitBreaker(name = "payment")
    public PaymentResponse findByOrder(UUID orderId) {
        try (AdmissionController.Permit ignored = admissionController.tryAcquire()) {
            return webClient.get().uri(paymentUrl + "/payments/orders/{orderId}", orderId)
                    .retrieve().bodyToMono(PaymentResponse.class)
                    .timeout(DeadlineContext.timeoutCappedAt(Duration.ofSeconds(2))).block();
        }
    }

    /**
     * Kompensacja ma własny stabilny klucz. Ponowienie po utracie odpowiedzi nie może
     * spowodować dwóch refundów, tak samo jak retry płatności nie może obciążyć dwa razy.
     */
    @Retry(name = "payment")
    @CircuitBreaker(name = "payment")
    public PaymentResponse refund(UUID orderId) {
        try (AdmissionController.Permit ignored = admissionController.tryAcquire()) {
            return webClient.post().uri(paymentUrl + "/payments/orders/{orderId}/refund", orderId)
                    .header("Idempotency-Key", "refund:" + orderId)
                    .retrieve().bodyToMono(PaymentResponse.class)
                    .timeout(DeadlineContext.timeoutCappedAt(Duration.ofSeconds(2))).block();
        }
    }

    /**
     * Fallback dla niedostępnego payment-service.
     *
     * Nie zwracamy sztucznej odpowiedzi "PAID", bo to byłoby niebezpieczne biznesowo.
     * Jeśli nie wiemy, czy płatność się udała, nie wolno udawać sukcesu.
     *
     * Zamiast tego rzucamy wyjątek, który OrderService przechwytuje i zamienia stan
     * zamówienia na PAYMENT_PENDING.
     *
     * Dzięki temu system zachowuje intencję zakupu, ale nie deklaruje fałszywie,
     * że płatność została zakończona.
     */
    private PaymentResponse paymentFallback(UUID orderId, String userId, BigDecimal amount, Throwable exception) {
        throw new IllegalStateException(
                "Payment provider unavailable. Order degraded to PAYMENT_PENDING.",
                exception
        );
    }

    /**
     * Request wysyłany do payment-mock-service.
     *
     * Jest prywatny, bo nie jest częścią publicznego API tego modułu.
     * To szczegół implementacyjny klienta HTTP.
     */
    private record PaymentRequest(UUID orderId, String userId, BigDecimal amount) {
    }

    /**
     * Odpowiedź z payment-mock-service.
     *
     * Minimalny kontrakt potrzebny Order Service do dalszego przetwarzania.
     */
    public record PaymentResponse(UUID paymentId, UUID orderId, String status) {
    }
}
