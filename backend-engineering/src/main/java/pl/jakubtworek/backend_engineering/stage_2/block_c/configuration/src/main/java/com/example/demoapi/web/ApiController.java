package pl.jakubtworek.backend_engineering.stage_2.block_c.configuration.src.main.java.com.example.demoapi.web;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import pl.jakubtworek.backend_engineering.stage_2.block_c.configuration.src.main.java.com.example.demoapi.config.SecretProperties;
import pl.jakubtworek.backend_engineering.stage_2.block_c.configuration.src.main.java.com.example.demoapi.health.ApplicationHealthState;
import pl.jakubtworek.backend_engineering.stage_2.block_c.configuration.src.main.java.com.example.demoapi.config.AppProperties;

import java.lang.management.ManagementFactory;
import java.util.Map;

@RestController
public class ApiController {

    private final AppProperties properties;
    private final SecretProperties secrets;
    private final ApplicationHealthState healthState;

    public ApiController(
            AppProperties properties,
            SecretProperties secrets,
            ApplicationHealthState healthState
    ) {
        this.properties = properties;
        this.secrets = secrets;
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
                "jvm", ManagementFactory.getRuntimeMXBean().getName(),
                "dbDsn", secrets.maskedDatabaseDsn()
        ));
    }

    @GetMapping("/admin/simulate-db-down")
    public ResponseEntity<String> simulateDbDown() {
        // This endpoint is only for local workshops and failure simulation.
        // It must not be exposed in production.
        healthState.simulateDependencyFailure();
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