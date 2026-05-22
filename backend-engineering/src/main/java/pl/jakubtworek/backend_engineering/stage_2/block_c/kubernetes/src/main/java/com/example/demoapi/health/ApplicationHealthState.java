package pl.jakubtworek.backend_engineering.stage_2.block_c.kubernetes.src.main.java.com.example.demoapi.health;

import org.springframework.stereotype.Component;
import pl.jakubtworek.backend_engineering.stage_2.block_c.docker.src.main.java.com.example.k8sapp.health.DependencyHealth;

import java.util.concurrent.atomic.AtomicBoolean;

@Component
public class ApplicationHealthState {

    private final DependencyHealth dependencyHealth;

    private final AtomicBoolean started = new AtomicBoolean(false);
    private final AtomicBoolean draining = new AtomicBoolean(false);
    private final AtomicBoolean deadlocked = new AtomicBoolean(false);
    private final AtomicBoolean dependencyFailureSimulation = new AtomicBoolean(false);

    public ApplicationHealthState(DependencyHealth dependencyHealth) {
        this.dependencyHealth = dependencyHealth;
    }

    public void markStarted() {
        started.set(true);
    }

    public void markDraining() {
        draining.set(true);
    }

    public void simulateDeadlock() {
        deadlocked.set(true);
    }

    public void simulateDependencyFailure() {
        dependencyFailureSimulation.set(true);
    }

    public boolean isStarted() {
        return started.get();
    }

    public boolean isDraining() {
        return draining.get();
    }

    public boolean isLive() {
        // Liveness should answer whether the process itself is broken.
        // It should not fail only because a database or another external service is temporarily unavailable.
        return !deadlocked.get();
    }

    public boolean isReady() {
        // Readiness should answer whether this Pod should receive traffic.
        // It may depend on startup completion, drain mode, and critical dependencies.
        return started.get()
                && !draining.get()
                && !dependencyFailureSimulation.get()
                && dependencyHealth.isHealthy();
    }
}