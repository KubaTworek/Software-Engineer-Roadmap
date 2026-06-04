package pl.jakubtworek.backend.notification.chaos;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/internal/chaos/notification")
public class NotificationChaosController {
    private final NotificationChaosSettings settings;

    public NotificationChaosController(NotificationChaosSettings settings) {
        this.settings = settings;
    }

    @GetMapping
    Map<String, Object> current() {
        return Map.of("processingDelayMs", settings.processingDelayMs());
    }

    @PostMapping("/processing-delay")
    Map<String, Object> setProcessingDelay(@RequestBody Map<String, Long> body) {
        settings.setProcessingDelayMs(body.getOrDefault("delayMs", 0L));
        return current();
    }

    @PostMapping("/reset")
    ResponseEntity<Void> reset() {
        settings.reset();
        return ResponseEntity.noContent().build();
    }
}
