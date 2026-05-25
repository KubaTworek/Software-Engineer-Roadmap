package pl.jakubtworek.backend_engineering.stage_3.block_a.concepts.src.main.java.pl.jakubtworek.systemdesign.concepts.cache;

import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory TTL cache used for tests and local examples.
 *
 * This is not a distributed cache.
 * In horizontally scaled production systems, Redis or another shared cache
 * is usually required.
 */
public class InMemoryTtlCache<K, V> implements CacheClient<K, V> {

    private final ConcurrentHashMap<K, Entry<V>> entries = new ConcurrentHashMap<>();

    @Override
    public Optional<V> get(K key) {
        Entry<V> entry = entries.get(key);

        if (entry == null) {
            return Optional.empty();
        }

        if (entry.isExpired()) {
            entries.remove(key);
            return Optional.empty();
        }

        return Optional.of(entry.value());
    }

    @Override
    public void put(K key, V value, Duration ttl) {
        entries.put(key, new Entry<>(value, System.nanoTime() + ttl.toNanos()));
    }

    @Override
    public void evict(K key) {
        entries.remove(key);
    }

    private record Entry<V>(V value, long expiresAtNanos) {

        boolean isExpired() {
            return System.nanoTime() > expiresAtNanos;
        }
    }
}