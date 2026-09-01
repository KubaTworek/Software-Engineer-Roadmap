package pl.jakubtworek.backend_engineering.stage_3.block_a.concepts.cache;

import java.time.Duration;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.LongSupplier;

/**
 * In-memory TTL cache used for tests and local examples.
 *
 * This is not a distributed cache.
 * In horizontally scaled production systems, Redis or another shared cache
 * is usually required.
 */
public class InMemoryTtlCache<K, V> implements CacheClient<K, V> {

    private final ConcurrentHashMap<K, Entry<V>> entries = new ConcurrentHashMap<>();
    private final LongSupplier nanoTime;

    public InMemoryTtlCache() {
        this(System::nanoTime);
    }

    public InMemoryTtlCache(LongSupplier nanoTime) {
        this.nanoTime = Objects.requireNonNull(nanoTime, "nanoTime is required");
    }

    @Override
    public Optional<V> get(K key) {
        Entry<V> entry = entries.get(Objects.requireNonNull(key, "key is required"));

        if (entry == null) {
            return Optional.empty();
        }

        if (entry.isExpired(nanoTime.getAsLong())) {
            entries.remove(key, entry);
            return Optional.empty();
        }

        return Optional.of(entry.value());
    }

    @Override
    public void put(K key, V value, Duration ttl) {
        Objects.requireNonNull(key, "key is required");
        Objects.requireNonNull(value, "value is required");
        Objects.requireNonNull(ttl, "ttl is required");
        if (ttl.isZero() || ttl.isNegative()) {
            throw new IllegalArgumentException("ttl must be positive");
        }
        entries.put(key, new Entry<>(value, nanoTime.getAsLong() + ttl.toNanos()));
    }

    @Override
    public void evict(K key) {
        entries.remove(Objects.requireNonNull(key, "key is required"));
    }

    private record Entry<V>(V value, long expiresAtNanos) {

        boolean isExpired(long nowNanos) {
            return nowNanos - expiresAtNanos >= 0;
        }
    }
}
