package pl.jakubtworek.backend.payment.api;

import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;
import pl.jakubtworek.backend.payment.chaos.PaymentChaosSettings;

import java.util.Random;
import java.util.UUID;

@RestController
@RequestMapping("/payments")
public class PaymentController {
    private static final Logger log = LoggerFactory.getLogger(PaymentController.class);
    private final Random random = new Random();
    private final PaymentChaosSettings chaosSettings;

    public PaymentController(PaymentChaosSettings chaosSettings) {
        this.chaosSettings = chaosSettings;
    }

    @PostMapping
    PaymentResponse pay(@Valid @RequestBody PaymentRequest request) throws InterruptedException {
        Thread.sleep(random.nextInt(Math.max(1, chaosSettings.maxDelayMs())));
        if (random.nextDouble() < chaosSettings.failureRate()) {
            log.warn("payment_failed orderId={} userId={} simulated=true", request.orderId(), request.userId());
            throw new IllegalStateException("Simulated payment provider failure");
        }
        PaymentResponse response = new PaymentResponse(UUID.randomUUID(), request.orderId(), "PAID");
        log.info("payment_paid orderId={} userId={} paymentId={}", request.orderId(), request.userId(), response.paymentId());
        return response;
    }
}
