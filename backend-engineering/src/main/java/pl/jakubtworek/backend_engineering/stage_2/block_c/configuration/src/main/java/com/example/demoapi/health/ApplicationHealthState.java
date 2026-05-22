package pl.jakubtworek.backend_engineering.stage_2.block_c.configuration.src.main.java.com.example.demoapi.health;

import org.springframework.stereotype.Component;

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
        // It should not fail only because an external dependency is temporarily unavailable.
        return !deadlocked.get();
    }

    public boolean isReady() {
        // Readiness should answer whether this Pod should receive traffic now.
        // A dependency outage should usually fail readiness, not liveness.
        return started.get()
                && !draining.get()
                && !dependencyFailureSimulation.get()
                && dependencyHealth.isHealthy();
    }
}