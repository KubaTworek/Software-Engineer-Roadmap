package pl.jakubtworek.backend_engineering.stage_1.block_a.virtual_threads;

import java.util.Objects;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Virtual threads remove the need to size a pool as a proxy for waiting cost.
 * A separate semaphore expresses the real capacity of a database, HTTP client
 * or another constrained downstream.
 */
public final class BoundedDownstream {

    private final Semaphore permits;
    private final AtomicInteger active = new AtomicInteger();
    private final AtomicInteger maximumActive = new AtomicInteger();

    public BoundedDownstream(int maximumConcurrency) {
        if (maximumConcurrency <= 0) {
            throw new IllegalArgumentException("maximumConcurrency must be positive");
        }
        this.permits = new Semaphore(maximumConcurrency, true);
    }

    public <T> T execute(InterruptibleOperation<T> operation) throws InterruptedException {
        Objects.requireNonNull(operation, "operation must not be null");
        permits.acquire();
        int nowActive = active.incrementAndGet();
        maximumActive.accumulateAndGet(nowActive, Math::max);
        try {
            return operation.execute();
        } finally {
            active.decrementAndGet();
            permits.release();
        }
    }

    public int activeOperations() {
        return active.get();
    }

    public int maximumObservedConcurrency() {
        return maximumActive.get();
    }
}
