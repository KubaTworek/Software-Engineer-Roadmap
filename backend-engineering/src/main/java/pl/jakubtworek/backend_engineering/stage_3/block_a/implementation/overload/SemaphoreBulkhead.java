package pl.jakubtworek.backend_engineering.stage_3.block_a.implementation.overload;

import java.util.Objects;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicInteger;

/** Fail-fast concurrency limit dedicated to one downstream dependency. */
public final class SemaphoreBulkhead {

    private final String dependency;
    private final int capacity;
    private final Semaphore permits;
    private final AtomicInteger active = new AtomicInteger();
    private final AtomicInteger rejected = new AtomicInteger();

    public SemaphoreBulkhead(String dependency, int capacity) {
        if (dependency == null || dependency.isBlank()) {
            throw new IllegalArgumentException("dependency is required");
        }
        if (capacity <= 0) {
            throw new IllegalArgumentException("capacity must be positive");
        }
        this.dependency = dependency;
        this.capacity = capacity;
        this.permits = new Semaphore(capacity);
    }

    public Permit enter() {
        if (!permits.tryAcquire()) {
            rejected.incrementAndGet();
            throw new BulkheadFullException(dependency);
        }
        active.incrementAndGet();
        return new Permit(this);
    }

    public Snapshot snapshot() {
        return new Snapshot(dependency, capacity, active.get(), rejected.get());
    }

    private void leave() {
        active.decrementAndGet();
        permits.release();
    }

    public record Snapshot(String dependency, int capacity, int active, int rejected) {
    }

    public static final class Permit implements AutoCloseable {

        private SemaphoreBulkhead owner;

        private Permit(SemaphoreBulkhead owner) {
            this.owner = Objects.requireNonNull(owner);
        }

        @Override
        public void close() {
            SemaphoreBulkhead current = owner;
            if (current != null) {
                owner = null;
                current.leave();
            }
        }
    }
}
