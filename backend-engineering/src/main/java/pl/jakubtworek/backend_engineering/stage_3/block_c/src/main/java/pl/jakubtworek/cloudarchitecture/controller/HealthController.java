package pl.jakubtworek.backend_engineering.stage_3.block_c.src.main.java.pl.jakubtworek.cloudarchitecture.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import pl.jakubtworek.backend_engineering.stage_3.block_c.src.main.java.pl.jakubtworek.cloudarchitecture.service.ReadinessService;

import java.time.Instant;
import java.util.Map;

/**
 * Exposes health and readiness endpoints.
 *
 * Health checks answer whether the process is alive.
 * Readiness checks answer whether the instance can safely receive traffic.
 */
@RestController
public class HealthController {
    private final ReadinessService readinessService;
    private final long startedAt = System.currentTimeMillis();

    public HealthController(ReadinessService readinessService) {
        this.readinessService = readinessService;
    }

    /**
     * Lightweight liveness check.
     *
     * This endpoint should avoid expensive dependency calls. It only confirms
     * that the application process is running.
     */
    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> health() {
        return ResponseEntity.ok(Map.of("status", "ok", "startedAt", Instant.ofEpochMilli(startedAt).toString()));
    }

    /**
     * Readiness check for external dependencies.
     *
     * This endpoint verifies whether the database and cache are reachable.
     * If it fails, the platform should avoid routing production traffic here.
     */
    @GetMapping("/ready")
    public ResponseEntity<Map<String, Object>> ready() {
        readinessService.verifyDependencies();
        return ResponseEntity.ok(Map.of("status", "ready"));
    }
}
