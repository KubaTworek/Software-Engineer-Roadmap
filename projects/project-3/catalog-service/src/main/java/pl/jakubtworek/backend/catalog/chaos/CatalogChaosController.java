package pl.jakubtworek.backend.catalog.chaos;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/internal/chaos/catalog")
public class CatalogChaosController {
    private final CatalogChaosSettings settings;

    public CatalogChaosController(CatalogChaosSettings settings) {
        this.settings = settings;
    }

    @GetMapping
    Map<String, Object> current() {
        return Map.of("databaseDelayMs", settings.databaseDelayMs());
    }

    @PostMapping("/db-delay")
    Map<String, Object> setDatabaseDelay(@RequestBody Map<String, Long> body) {
        long delayMs = body.getOrDefault("delayMs", 0L);
        settings.setDatabaseDelayMs(delayMs);
        return Map.of("databaseDelayMs", settings.databaseDelayMs());
    }

    @PostMapping("/reset")
    ResponseEntity<Void> reset() {
        settings.reset();
        return ResponseEntity.noContent().build();
    }
}
