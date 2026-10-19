package com.example.ratelimiter.core;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;
import org.springframework.stereotype.Component;

/**
 * Small circuit breaker for the critical path. Once Redis is demonstrably down,
 * requests immediately enter their configured fallback instead of each waiting
 * for the client timeout and amplifying saturation.
 */
@Component
public class RedisAvailabilityGate {
    private static final int FAILURE_THRESHOLD = 3;
    private static final long OPEN_NANOS = Duration.ofSeconds(2).toNanos();
    private final AtomicInteger failures = new AtomicInteger();
    private final AtomicLong openUntil = new AtomicLong();

    public <T> T call(Supplier<T> operation) {
        long now = System.nanoTime();
        if (now < openUntil.get()) throw new RedisCircuitOpenException();
        try {
            T result = operation.get();
            failures.set(0); openUntil.set(0);
            return result;
        } catch (RuntimeException exception) {
            if (failures.incrementAndGet() >= FAILURE_THRESHOLD) openUntil.set(now + OPEN_NANOS);
            throw exception;
        }
    }

    /** Gauge-friendly view; no wall clock is used for elapsed-time correctness. */
    public boolean isOpen() {
        return System.nanoTime() < openUntil.get();
    }

    public static class RedisCircuitOpenException extends RuntimeException {
        public RedisCircuitOpenException() { super("Redis circuit is open"); }
    }
}
