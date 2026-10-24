package pl.jakubtworek.lld.cache.domain;

import java.time.Duration;
import java.util.Optional;

/**
 * Generyczny kontrakt jednowątkowego cache. Get dotyka recency/częstotliwości
 * i liczy hit/miss. Peek nie dotyka, ale może wygaszać. Snapshot też sprząta;
 * statistics i diagnostics są czystymi odczytami bez czasu i sprzątania.
 */
public interface Cache<K, V> {

    Optional<V> get(K key);

    Optional<V> peek(K key);

    void put(K key, V value);

    void put(K key, V value, Duration ttl);

    Optional<V> invalidate(K key);

    void clear();

    int size();

    int cleanUp();

    CacheSnapshot<K, V> snapshot();

    ResizeResult<K, V> resize(int capacity);

    CacheStatistics statistics();

    CacheDiagnostics diagnostics();

    void resetStatistics();
}
