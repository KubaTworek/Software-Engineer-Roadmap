package pl.jakubtworek.backend_engineering.stage_3.block_a.implementation.src.main.java.pl.jakubtworek.systemdesign.implementation.cache;

import java.time.Duration;
import java.util.Optional;

/**
 * Generic cache abstraction.
 *
 * The implementation may be backed by Redis, Caffeine, Memcached,
 * or any managed cache service.
 */
public interface CacheClient<K, V> {

    Optional<V> get(K key);

    void put(K key, V value, Duration ttl);

    void evict(K key);
}