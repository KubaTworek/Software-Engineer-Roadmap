package pl.jakubtworek.backend_engineering.stage_2.block_c.workshop.src.main.java.com.example.demoapi.web;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import pl.jakubtworek.backend_engineering.stage_2.block_c.workshop.src.main.java.com.example.demoapi.health.ApplicationHealthState;
import pl.jakubtworek.backend_engineering.stage_2.block_c.workshop.src.main.java.com.example.demoapi.config.AppProperties;

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
                "status", "ok",
                "imageTag", properties.getImageTag(),
                "commitSha", properties.getCommitSha()
        ));
    }

    @GetMapping("/admin/simulate-readiness-failure")
    public ResponseEntity<String> simulateReadinessFailure() {
        // This endpoint is for local workshops only.
        // It demonstrates a Running Pod that is not Ready.
        healthState.simulateReadinessFailure();
        return ResponseEntity.ok("readiness_failure=true");
    }

    @GetMapping("/admin/simulate-deadlock")
    public ResponseEntity<String> simulateDeadlock() {
        // This endpoint is for local workshops only.
        // It demonstrates a liveness failure leading to container restart.
        healthState.simulateDeadlock();
        return ResponseEntity.ok("deadlocked=true");
    }
}