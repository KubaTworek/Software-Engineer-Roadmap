package pl.jakubtworek.backend_engineering.stage_3.block_a.concepts.src.main.java.pl.jakubtworek.systemdesign.concepts.cache;

import java.time.Duration;
import java.util.Optional;

/**
 * Minimal cache abstraction.
 *
 * A real implementation may use Redis, Caffeine, Memcached,
 * or a managed cloud cache service.
 */
public interface CacheClient<K, V> {

    Optional<V> get(K key);

    void put(K key, V value, Duration ttl);

    void evict(K key);
}