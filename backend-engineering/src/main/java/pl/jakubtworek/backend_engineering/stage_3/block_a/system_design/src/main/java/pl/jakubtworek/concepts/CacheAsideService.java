package pl.jakubtworek.backend_engineering.stage_3.block_a.system_design.src.main.java.pl.jakubtworek.concepts;

import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

/**
 * Implements the cache-aside pattern.
 *
 * Flow:
 * 1. Try to read from cache.
 * 2. On cache miss, read from the source of truth.
 * 3. Store the value in cache.
 * 4. Return the value to the caller.
 *
 * This class also includes a simple single-flight mechanism that prevents
 * multiple concurrent refreshes of the same key inside one JVM process.
 */
public class CacheAsideService<K, V> {

    private final Cache<K, V> cache;
    private final Function<K, V> sourceOfTruth;
    private final Duration ttl;
    private final boolean singleFlightEnabled;
    private final ConcurrentHashMap<K, Object> locks = new ConcurrentHashMap<>();

    public CacheAsideService(
            Cache<K, V> cache,
            Function<K, V> sourceOfTruth,
            Duration ttl,
            boolean singleFlightEnabled
    ) {
        this.cache = Objects.requireNonNull(cache);
        this.sourceOfTruth = Objects.requireNonNull(sourceOfTruth);
        this.ttl = Objects.requireNonNull(ttl);
        this.singleFlightEnabled = singleFlightEnabled;
    }

    /**
     * Reads a value using cache-aside.
     *
     * In distributed systems, this local single-flight mechanism should usually
     * be complemented with distributed locking, request coalescing at the edge,
     * stale-while-revalidate, or TTL jitter.
     */
    public V get(K key) {
        return cache.get(key).orElseGet(() -> {
            if (!singleFlightEnabled) {
                return refresh(key);
            }

            Object lock = locks.computeIfAbsent(key, ignored -> new Object());
            try {
                synchronized (lock) {
                    return cache.get(key).orElseGet(() -> refresh(key));
                }
            } finally {
                locks.remove(key, lock);
            }
        });
    }

    /**
     * Writes to the source of truth should invalidate or refresh the cache.
     *
     * This method models invalidation only. The actual database write belongs
     * to the application service that owns the business transaction.
     */
    public void invalidate(K key) {
        cache.invalidate(key);
    }

    private V refresh(K key) {
        V value = sourceOfTruth.apply(key);
        cache.put(key, value, ttl);
        return value;
    }
}
