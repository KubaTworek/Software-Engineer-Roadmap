package pl.jakubtworek.backend_engineering.stage_2.block_c.docker.src.main.java.com.example.k8sapp.web;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import pl.jakubtworek.backend_engineering.stage_2.block_c.docker.src.main.java.com.example.k8sapp.health.ApplicationHealthState;

import java.lang.management.ManagementFactory;
import java.util.Map;

@RestController
public class AppController {

    private final ApplicationHealthState healthState;

    public AppController(ApplicationHealthState healthState) {
        this.healthState = healthState;
    }

    @GetMapping("/")
    public ResponseEntity<Map<String, Object>> index() {
        if (healthState.isDraining()) {
            return ResponseEntity
                    .status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(Map.of("status", "draining"));
        }

        return ResponseEntity.ok(Map.of(
                "ok", true,
                "pid", ProcessHandle.current().pid(),
                "jvm", ManagementFactory.getRuntimeMXBean().getName()
        ));
    }

    @GetMapping("/admin/simulate-db-down")
    public ResponseEntity<String> simulateDbDown() {
        // This endpoint is only for local workshops and failure simulation.
        // It should not be exposed in a real production service.
        healthState.simulateDependencyDown();
        return ResponseEntity.ok("db_down=true");
    }

    @GetMapping("/admin/simulate-deadlock")
    public ResponseEntity<String> simulateDeadlock() {
        // This endpoint is only for local workshops and failure simulation.
        // It should not be exposed in a real production service.
        healthState.simulateDeadlock();
        return ResponseEntity.ok("deadlocked=true");
    }
}