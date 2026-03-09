package pl.jakubtworek.backend_systems_lab_stage_1.block_a.threads.concurrent_hash_map;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Correct implementation.
 *
 * computeIfAbsent guarantees:
 *
 * - atomic check + compute + put
 * - mapping function executed at most once per key
 * - no external synchronization required
 *
 * Important:
 * mapping function may be executed more than once
 * in extreme contention but only one result is inserted.
 */
public class AtomicCache {

    private final ConcurrentHashMap<String, String> cache =
            new ConcurrentHashMap<>();

    private final AtomicInteger computeCounter = new AtomicInteger(0);

    public String getData(String key) {
        return cache.computeIfAbsent(key, this::expensiveCompute);
    }

    private String expensiveCompute(String key) {
        computeCounter.incrementAndGet();
        sleep(50);
        return "Value-" + key;
    }

    public int getComputeCount() {
        return computeCounter.get();
    }

    private void sleep(long ms) {
        try { Thread.sleep(ms); } catch (InterruptedException ignored) {}
    }
}