package pl.jakubtworek.backend.payment.chaos;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/internal/chaos/payment")
public class PaymentChaosController {
    private final PaymentChaosSettings settings;

    public PaymentChaosController(PaymentChaosSettings settings) {
        this.settings = settings;
    }

    @GetMapping
    Map<String, Object> current() {
        return Map.of(
                "failureRate", settings.failureRate(),
                "maxDelayMs", settings.maxDelayMs()
        );
    }

    @PostMapping
    Map<String, Object> update(@RequestBody Map<String, Number> body) {
        if (body.containsKey("failureRate")) {
            settings.setFailureRate(body.get("failureRate").doubleValue());
        }
        if (body.containsKey("maxDelayMs")) {
            settings.setMaxDelayMs(body.get("maxDelayMs").intValue());
        }
        return current();
    }

    @PostMapping("/reset")
    ResponseEntity<Void> reset() {
        settings.reset();
        return ResponseEntity.noContent().build();
    }
}
