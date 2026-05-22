package pl.jakubtworek.backend_engineering.stage_2.block_c.kubernetes.src.main.java.com.example.demoapi.web;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import pl.jakubtworek.backend_engineering.stage_2.block_c.docker.src.main.java.com.example.k8sapp.health.ApplicationHealthState;
import pl.jakubtworek.backend_engineering.stage_2.block_c.kubernetes.src.main.java.com.example.demoapi.config.AppProperties;

import java.lang.management.ManagementFactory;
import java.util.Map;

@RestController
public class ApiController {

    private final AppProperties properties;
    private final ApplicationHealthState healthState;

    public ApiController(AppProperties properties, ApplicationHealthState healthState) {
        this.properties = properties;
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
                "application", properties.getName(),
                "ok", true,
                "pid", ProcessHandle.current().pid(),
                "jvm", ManagementFactory.getRuntimeMXBean().getName()
        ));
    }

    @GetMapping("/admin/simulate-db-down")
    public ResponseEntity<String> simulateDbDown() {
        // This endpoint is only for local workshops and failure simulation.
        // It must not be exposed in production.
        healthState.simulateDependencyDown();
        return ResponseEntity.ok("dependency_failure=true");
    }

    @GetMapping("/admin/simulate-deadlock")
    public ResponseEntity<String> simulateDeadlock() {
        // This endpoint is only for local workshops and failure simulation.
        // It must not be exposed in production.
        healthState.simulateDeadlock();
        return ResponseEntity.ok("deadlocked=true");
    }
}