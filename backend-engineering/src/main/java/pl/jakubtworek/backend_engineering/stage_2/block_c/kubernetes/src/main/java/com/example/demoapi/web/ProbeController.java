package pl.jakubtworek.backend_engineering.stage_2.block_c.kubernetes.src.main.java.com.example.demoapi.web;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import pl.jakubtworek.backend_engineering.stage_2.block_c.docker.src.main.java.com.example.k8sapp.health.ApplicationHealthState;

@RestController
public class ProbeController {

    private final ApplicationHealthState healthState;

    public ProbeController(ApplicationHealthState healthState) {
        this.healthState = healthState;
    }

    @GetMapping("/startupz")
    public ResponseEntity<String> startup() {
        // startupProbe should pass only after the application has completed its startup phase.
        if (healthState.isStarted()) {
            return ResponseEntity.ok("ok");
        }

        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body("starting");
    }

    @GetMapping("/readyz")
    public ResponseEntity<String> ready() {
        // readinessProbe controls whether this Pod receives traffic through a Service.
        if (healthState.isReady()) {
            return ResponseEntity.ok("ready");
        }

        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body("not-ready");
    }

    @GetMapping("/livez")
    public ResponseEntity<String> live() {
        // livenessProbe controls whether Kubernetes should restart this container.
        if (healthState.isLive()) {
            return ResponseEntity.ok("ok");
        }

        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("deadlocked");
    }
}