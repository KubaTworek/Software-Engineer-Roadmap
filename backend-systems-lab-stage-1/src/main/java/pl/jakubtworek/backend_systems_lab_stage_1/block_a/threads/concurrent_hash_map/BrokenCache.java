package pl.jakubtworek.backend_systems_lab_stage_1.block_a.threads.concurrent_hash_map;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Example of a non-atomic cache implementation.
 *
 * In this code:
 * - key existence is checked using containsKey()
 * - value is computed and inserted with put()
 *
 * These operations are executed separately,
 * which creates a race condition under concurrent access.
 */
public class BrokenCache {

    // concurrent map storing cached values
    private final ConcurrentHashMap<String, String> cache =
            new ConcurrentHashMap<>();

    // counts how many times the expensive computation runs
    private final AtomicInteger computeCounter = new AtomicInteger(0);

    public String getData(String key) {

        // check if value already exists in cache
        if (!cache.containsKey(key)) {

            // if missing, compute and store the value
            // another thread may do the same at the same time
            cache.put(key, expensiveCompute(key));
        }

        // return cached value
        return cache.get(key);
    }

    private String expensiveCompute(String key) {

        // track number of times the computation was executed
        computeCounter.incrementAndGet();

        // simulate slow computation (e.g. database call)
        sleep(50);

        return "Value-" + key;
    }

    public int getComputeCount() {

        // used to observe duplicate computations
        return computeCounter.get();
    }

    private void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException ignored) {
            // interruption ignored for simplicity
        }
    }
}