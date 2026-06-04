package pl.jakubtworek.backend.payment.api;

import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;

import java.util.Random;
import java.util.UUID;

@RestController
@RequestMapping("/payments")
public class PaymentController {
    private static final Logger log = LoggerFactory.getLogger(PaymentController.class);
    private final Random random = new Random();
    private final double failureRate;
    private final int maxDelayMs;

    public PaymentController(@Value("${payment.failure-rate:0.2}") double failureRate,
                             @Value("${payment.max-delay-ms:1500}") int maxDelayMs) {
        this.failureRate = failureRate;
        this.maxDelayMs = maxDelayMs;
    }

    @PostMapping
    PaymentResponse pay(@Valid @RequestBody PaymentRequest request) throws InterruptedException {
        Thread.sleep(random.nextInt(Math.max(1, maxDelayMs)));
        if (random.nextDouble() < failureRate) {
            log.warn("payment_failed orderId={} userId={} simulated=true", request.orderId(), request.userId());
            throw new IllegalStateException("Simulated payment provider failure");
        }
        PaymentResponse response = new PaymentResponse(UUID.randomUUID(), request.orderId(), "PAID");
        log.info("payment_paid orderId={} userId={} paymentId={}", request.orderId(), request.userId(), response.paymentId());
        return response;
    }
}
