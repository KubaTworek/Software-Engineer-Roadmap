package pl.jakubtworek.backend_engineering.stage_3.block_a.implementation.src.main.java.pl.jakubtworek.systemdesign.implementation.cache;

import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

/**
 * Real cache-aside implementation with local single-flight protection.
 *
 * Flow:
 * 1. Try cache.
 * 2. On miss, acquire a per-key lock.
 * 3. Re-check cache after acquiring the lock.
 * 4. Load from the source of truth.
 * 5. Store in cache with TTL + jitter.
 *
 * The single-flight protection here works inside one JVM.
 * In a multi-replica system, use distributed coordination or request coalescing
 * at cache/gateway/CDN level for full protection.
 */
public class CacheAsideService<K, V> {

    private final CacheClient<K, V> cache;
    private final Function<K, V> sourceOfTruthLoader;
    private final Duration ttl;
    private final TtlJitter ttlJitter;
    private final ConcurrentHashMap<K, Object> locks = new ConcurrentHashMap<>();

    public CacheAsideService(
            CacheClient<K, V> cache,
            Function<K, V> sourceOfTruthLoader,
            Duration ttl,
            TtlJitter ttlJitter
    ) {
        this.cache = Objects.requireNonNull(cache);
        this.sourceOfTruthLoader = Objects.requireNonNull(sourceOfTruthLoader);
        this.ttl = Objects.requireNonNull(ttl);
        this.ttlJitter = Objects.requireNonNull(ttlJitter);
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
                    V value = sourceOfTruthLoader.apply(key);
                    cache.put(key, value, ttlJitter.apply(ttl));
                    return value;
                });
            }
        } finally {
            locks.remove(key, lock);
        }
    }
}