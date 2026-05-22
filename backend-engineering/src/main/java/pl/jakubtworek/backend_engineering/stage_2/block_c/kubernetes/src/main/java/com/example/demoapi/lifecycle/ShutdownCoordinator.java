package pl.jakubtworek.backend_engineering.stage_2.block_c.kubernetes.src.main.java.com.example.demoapi.lifecycle;

import jakarta.annotation.PreDestroy;
import org.springframework.stereotype.Component;
import pl.jakubtworek.backend_engineering.stage_2.block_c.docker.src.main.java.com.example.k8sapp.health.ApplicationHealthState;

@Component
public class ShutdownCoordinator {

    private final ApplicationHealthState healthState;

    public ShutdownCoordinator(ApplicationHealthState healthState) {
        this.healthState = healthState;
    }

    @PreDestroy
    public void onShutdown() {
        // Kubernetes sends SIGTERM before the Pod is forcefully killed.
        // Marking the application as draining makes readiness fail during termination.
        healthState.markDraining();

        // Spring Boot graceful shutdown should finish in-flight requests according to configuration.
        // Do not block forever here; shutdown logic must always be bounded.
        System.out.println("Shutdown started; application entered drain mode.");
    }
}