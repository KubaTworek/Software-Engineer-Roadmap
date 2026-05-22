package pl.jakubtworek.backend_engineering.stage_2.block_c.docker.src.main.java.com.example.k8sapp.lifecycle;

import jakarta.annotation.PreDestroy;
import org.springframework.stereotype.Component;
import pl.jakubtworek.backend_engineering.stage_2.block_c.docker.src.main.java.com.example.k8sapp.health.ApplicationHealthState;

@Component
public class GracefulShutdownService {

    private final ApplicationHealthState healthState;

    public GracefulShutdownService(ApplicationHealthState healthState) {
        this.healthState = healthState;
    }

    @PreDestroy
    public void onShutdown() {
        // Entering drain mode should make readiness fail before the process exits.
        // This gives Kubernetes a chance to stop routing new traffic to this Pod.
        healthState.markDraining();

        // Spring Boot will continue with its own graceful shutdown if configured.
        // Avoid blocking forever here; long-running cleanup should be bounded.
        System.out.println("Application is entering drain mode");
    }
}