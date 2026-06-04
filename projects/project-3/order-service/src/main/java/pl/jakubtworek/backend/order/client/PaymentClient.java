package pl.jakubtworek.backend.order.client;

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.UUID;

@Component
public class PaymentClient {
    private final WebClient webClient;
    private final String paymentUrl;

    public PaymentClient(WebClient.Builder builder, @Value("${services.payment.url}") String paymentUrl) {
        this.webClient = builder.build();
        this.paymentUrl = paymentUrl;
    }

    @Retry(name = "payment")
    @CircuitBreaker(name = "payment", fallbackMethod = "paymentFallback")
    public PaymentResponse pay(UUID orderId, String userId, BigDecimal amount) {
        return webClient.post()
                .uri(paymentUrl + "/payments")
                .bodyValue(new PaymentRequest(orderId, userId, amount))
                .retrieve()
                .bodyToMono(PaymentResponse.class)
                .timeout(Duration.ofSeconds(2))
                .block();
    }

    private PaymentResponse paymentFallback(UUID orderId, String userId, BigDecimal amount, Throwable exception) {
        throw new IllegalStateException("Payment provider unavailable. Order degraded to PAYMENT_PENDING.", exception);
    }

    private record PaymentRequest(UUID orderId, String userId, BigDecimal amount) {}
    public record PaymentResponse(UUID paymentId, UUID orderId, String status) {}
}
