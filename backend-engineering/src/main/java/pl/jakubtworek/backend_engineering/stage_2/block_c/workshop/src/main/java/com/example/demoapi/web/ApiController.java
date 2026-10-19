package com.example.demoapi.web;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import com.example.demoapi.health.ApplicationHealthState;
import com.example.demoapi.config.AppProperties;
import com.example.demoapi.config.SecretProperties;

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
                "status", "ok",
                "imageTag", properties.getImageTag(),
                "commitSha", properties.getCommitSha(),
                "databaseDsn", secrets.maskedDatabaseDsn()
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
