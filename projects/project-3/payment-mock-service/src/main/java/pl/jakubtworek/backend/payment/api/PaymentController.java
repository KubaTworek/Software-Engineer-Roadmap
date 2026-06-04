package pl.jakubtworek.backend.payment.api;

import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;

import java.util.Random;
import java.util.UUID;

@RestController
@RequestMapping("/payments")
public class PaymentController {
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
            throw new IllegalStateException("Simulated payment provider failure");
        }
        return new PaymentResponse(UUID.randomUUID(), request.orderId(), "PAID");
    }
}
