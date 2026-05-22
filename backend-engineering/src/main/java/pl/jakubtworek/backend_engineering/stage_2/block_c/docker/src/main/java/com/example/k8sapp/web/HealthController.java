package pl.jakubtworek.backend_engineering.stage_2.block_c.docker.src.main.java.com.example.k8sapp.web;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import pl.jakubtworek.backend_engineering.stage_2.block_c.docker.src.main.java.com.example.k8sapp.health.ApplicationHealthState;

/**
 * Custom Kubernetes probe endpoints.
 *
 * These endpoints are intentionally explicit because they document the difference
 * between startup, readiness, and liveness from the application perspective.
 */
@RestController
public class HealthController {

    private final ApplicationHealthState healthState;

    public HealthController(ApplicationHealthState healthState) {
        this.healthState = healthState;
    }

    @GetMapping("/startupz")
    public ResponseEntity<String> startup() {
        if (healthState.isStarted()) {
            return ResponseEntity.ok("ok");
        }

        return ResponseEntity
                .status(HttpStatus.SERVICE_UNAVAILABLE)
                .body("starting");
    }

    @GetMapping("/readyz")
    public ResponseEntity<String> ready() {
        if (healthState.isReady()) {
            return ResponseEntity.ok("ready");
        }

        return ResponseEntity
                .status(HttpStatus.SERVICE_UNAVAILABLE)
                .body("not-ready");
    }

    @GetMapping("/livez")
    public ResponseEntity<String> live() {
        if (healthState.isLive()) {
            return ResponseEntity.ok("ok");
        }

        return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body("deadlocked");
    }
}