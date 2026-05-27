package pl.jakubtworek.backend_engineering.stage_1.block_a.concurrent_hash_map;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Thread-safe cache implementation.
 *
 * computeIfAbsent performs:
 * - atomic lookup of the key
 * - computation of the value if the key is missing
 * - insertion of the computed value into the map
 *
 * No additional synchronization is required when accessing the cache.
 */
public class AtomicCache {

    // concurrent map storing computed values
    private final ConcurrentHashMap<String, String> cache =
            new ConcurrentHashMap<>();

    // counts how many times the expensive computation was executed
    private final AtomicInteger computeCounter = new AtomicInteger(0);

    public String getData(String key) {

        // if key exists -> return cached value
        // if key is missing -> compute value using expensiveCompute()
        return cache.computeIfAbsent(key, this::expensiveCompute);
    }

    private String expensiveCompute(String key) {

        // track how many times computation actually runs
        computeCounter.incrementAndGet();

        // simulate slow operation (e.g. DB call or remote API)
        sleep(50);

        return "Value-" + key;
    }

    public int getComputeCount() {

        // used to verify how many times computation occurred
        return computeCounter.get();
    }

    private void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException ignored) {
            // interruption ignored in this simple simulation
        }
    }
}