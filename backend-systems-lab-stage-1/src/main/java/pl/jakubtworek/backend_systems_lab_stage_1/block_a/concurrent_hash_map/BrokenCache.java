package pl.jakubtworek.backend_systems_lab_stage_1.block_a.concurrent_hash_map;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Broken implementation.
 *
 * Problem:
 * containsKey + put is NOT atomic.
 *
 * Two threads can:
 *  - both see key missing
 *  - both compute value
 *  - both insert
 *
 * Result:
 *  - expensiveCompute executed multiple times
 */
public class BrokenCache {

    private final ConcurrentHashMap<String, String> cache =
            new ConcurrentHashMap<>();

    private final AtomicInteger computeCounter = new AtomicInteger(0);

    public String getData(String key) {
        if (!cache.containsKey(key)) {
            cache.put(key, expensiveCompute(key));
        }
        return cache.get(key);
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