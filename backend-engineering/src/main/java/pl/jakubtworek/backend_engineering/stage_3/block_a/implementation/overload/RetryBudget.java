package pl.jakubtworek.backend_engineering.stage_3.block_a.implementation.overload;

import java.util.concurrent.atomic.AtomicInteger;

/** Shared retry allowance; first attempts are free, every repeated attempt costs one token. */
public final class RetryBudget {

    private final int capacity;
    private final AtomicInteger remaining;
    private final AtomicInteger consumed = new AtomicInteger();
    private final AtomicInteger denied = new AtomicInteger();

    public RetryBudget(int capacity) {
        if (capacity < 0) {
            throw new IllegalArgumentException("capacity must not be negative");
        }
        this.capacity = capacity;
        this.remaining = new AtomicInteger(capacity);
    }

    public boolean tryConsumeRetry() {
        while (true) {
            int current = remaining.get();
            if (current == 0) {
                denied.incrementAndGet();
                return false;
            }
            if (remaining.compareAndSet(current, current - 1)) {
                consumed.incrementAndGet();
                return true;
            }
        }
    }

    public Snapshot snapshot() {
        return new Snapshot(capacity, remaining.get(), consumed.get(), denied.get());
    }

    public record Snapshot(int capacity, int remaining, int consumed, int denied) {
    }
}
