package pl.jakubtworek.backend_engineering.stage_2.block_c.workshop.src.main.java.com.example.demoapi.lifecycle;

import jakarta.annotation.PreDestroy;
import org.springframework.stereotype.Component;
import pl.jakubtworek.backend_engineering.stage_2.block_c.workshop.src.main.java.com.example.demoapi.health.ApplicationHealthState;

@Component
public class ShutdownCoordinator {

    private final ApplicationHealthState healthState;

    public ShutdownCoordinator(ApplicationHealthState healthState) {
        this.healthState = healthState;
    }

    @PreDestroy
    public void onShutdown() {
        // Kubernetes sends SIGTERM before killing the container.
        // Marking the app as draining should make readiness fail and stop new traffic.
        healthState.markDraining();
        System.out.println("Application entered drain mode.");
    }
}