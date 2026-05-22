package pl.jakubtworek.backend_engineering.stage_2.block_c.docker.src.main.java.com.example.k8sapp.health;

import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicBoolean;

@Component
public class ApplicationHealthState {

    private final DependencyHealth dependencyHealth;

    private final AtomicBoolean started = new AtomicBoolean(false);
    private final AtomicBoolean draining = new AtomicBoolean(false);
    private final AtomicBoolean deadlocked = new AtomicBoolean(false);
    private final AtomicBoolean dependencyDownSimulation = new AtomicBoolean(false);

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

    public void simulateDependencyDown() {
        dependencyDownSimulation.set(true);
    }

    public boolean isStarted() {
        return started.get();
    }

    public boolean isDraining() {
        return draining.get();
    }

    public boolean isLive() {
        // Liveness should answer whether the process is internally broken.
        // It should not fail only because an external dependency is temporarily unavailable.
        return !deadlocked.get();
    }

    public boolean isReady() {
        // Readiness should answer whether this Pod should receive traffic.
        // It is allowed to depend on startup, drain mode, and critical dependencies.
        return started.get()
                && !draining.get()
                && !dependencyDownSimulation.get()
                && dependencyHealth.isHealthy();
    }
}