package pl.jakubtworek.backend_engineering.stage_2.block_c.configuration.src.main.java.com.example.demoapi.lifecycle;

import jakarta.annotation.PreDestroy;
import org.springframework.stereotype.Component;
import pl.jakubtworek.backend_engineering.stage_2.block_c.configuration.src.main.java.com.example.demoapi.health.ApplicationHealthState;

@Component
public class ShutdownCoordinator {

    private final ApplicationHealthState healthState;

    public ShutdownCoordinator(ApplicationHealthState healthState) {
        this.healthState = healthState;
    }

    @PreDestroy
    public void onShutdown() {
        // Kubernetes sends SIGTERM before forcefully killing the container.
        // Marking the application as draining should make readiness fail.
        healthState.markDraining();

        // Spring Boot graceful shutdown should handle in-flight requests according to configuration.
        System.out.println("Application entered drain mode.");
    }
}