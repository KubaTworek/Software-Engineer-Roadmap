package pl.jakubtworek.backend_engineering.stage_3.block_a.system_design.src.main.java.pl.jakubtworek.concepts;

import java.time.Duration;
import java.util.Optional;

/**
 * Minimal cache abstraction.
 *
 * A real implementation could use Redis, Memcached, an in-memory cache,
 * or a managed cloud cache service.
 */
public interface Cache<K, V> {

    Optional<V> get(K key);

    void put(K key, V value, Duration ttl);

    void invalidate(K key);
}
