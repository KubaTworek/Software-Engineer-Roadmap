package pl.jakubtworek.backend_engineering.stage_2.block_c.workshop.src.main.java.com.example.demoapi.health;

import org.springframework.stereotype.Component;
import java.util.concurrent.atomic.AtomicBoolean;

@Component
public class ApplicationHealthState {

    private final AtomicBoolean started = new AtomicBoolean(false);
    private final AtomicBoolean draining = new AtomicBoolean(false);
    private final AtomicBoolean deadlocked = new AtomicBoolean(false);
    private final AtomicBoolean readinessFailureSimulation = new AtomicBoolean(false);

    public void markStarted() {
        started.set(true);
    }

    public void markDraining() {
        draining.set(true);
    }

    public void simulateDeadlock() {
        deadlocked.set(true);
    }

    public void simulateReadinessFailure() {
        readinessFailureSimulation.set(true);
    }

    public boolean isStarted() {
        return started.get();
    }

    public boolean isDraining() {
        return draining.get();
    }

    public boolean isReady() {
        // Readiness controls whether this Pod should receive traffic through a Service.
        // This may fail while the process is still alive and should not necessarily trigger a restart.
        return started.get() && !draining.get() && !readinessFailureSimulation.get();
    }

    public boolean isLive() {
        // Liveness controls whether kubelet should restart the container.
        // It should fail only when the process is internally broken.
        return !deadlocked.get();
    }
}