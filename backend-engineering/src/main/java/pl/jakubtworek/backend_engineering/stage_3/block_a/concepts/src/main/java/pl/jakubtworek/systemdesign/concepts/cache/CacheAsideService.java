package pl.jakubtworek.backend_engineering.stage_3.block_a.concepts.src.main.java.pl.jakubtworek.systemdesign.concepts.cache;

import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

/**
 * Implements cache-aside with local single-flight protection.
 *
 * Request flow:
 * 1. Read from cache.
 * 2. If miss, lock per key.
 * 3. Re-check cache after acquiring the lock.
 * 4. Load from source of truth.
 * 5. Store in cache with TTL + jitter.
 *
 * The single-flight part works inside one JVM.
 * For many replicas, use distributed locking, request coalescing,
 * or stale-while-revalidate at a shared layer.
 */
public class CacheAsideService<K, V> {

    private final CacheClient<K, V> cache;
    private final Function<K, V> sourceOfTruth;
    private final Duration ttl;
    private final TtlJitter jitter;
    private final ConcurrentHashMap<K, Object> locks = new ConcurrentHashMap<>();

    public CacheAsideService(
            CacheClient<K, V> cache,
            Function<K, V> sourceOfTruth,
            Duration ttl,
            TtlJitter jitter
    ) {
        this.cache = Objects.requireNonNull(cache);
        this.sourceOfTruth = Objects.requireNonNull(sourceOfTruth);
        this.ttl = Objects.requireNonNull(ttl);
        this.jitter = Objects.requireNonNull(jitter);
    }

    public V get(K key) {
        return cache.get(key).orElseGet(() -> loadWithSingleFlight(key));
    }

    public void invalidate(K key) {
        cache.evict(key);
    }

    private V loadWithSingleFlight(K key) {
        Object lock = locks.computeIfAbsent(key, ignored -> new Object());

        try {
            synchronized (lock) {
                return cache.get(key).orElseGet(() -> {
                    V value = sourceOfTruth.apply(key);
                    cache.put(key, value, jitter.apply(ttl));
                    return value;
                });
            }
        } finally {
            locks.remove(key, lock);
        }
    }
}