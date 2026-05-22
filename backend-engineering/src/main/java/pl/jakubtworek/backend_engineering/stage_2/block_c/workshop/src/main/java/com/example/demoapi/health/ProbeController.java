package pl.jakubtworek.backend_engineering.stage_2.block_c.workshop.src.main.java.com.example.demoapi.health;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Explicit Kubernetes probe endpoints.
 *
 * startupz: protects slow startup.
 * readyz: controls Service endpoints.
 * livez: controls container restart.
 */
@RestController
public class ProbeController {

    private final ApplicationHealthState healthState;

    public ProbeController(ApplicationHealthState healthState) {
        this.healthState = healthState;
    }

    @GetMapping("/startupz")
    public ResponseEntity<String> startup() {
        if (healthState.isStarted()) {
            return ResponseEntity.ok("ok");
        }

        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body("starting");
    }

    @GetMapping("/readyz")
    public ResponseEntity<String> readiness() {
        if (healthState.isReady()) {
            return ResponseEntity.ok("ready");
        }

        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body("not-ready");
    }

    @GetMapping("/livez")
    public ResponseEntity<String> liveness() {
        if (healthState.isLive()) {
            return ResponseEntity.ok("ok");
        }

        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("deadlocked");
    }
}