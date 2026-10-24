package pl.jakubtworek.lld.cache.domain;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * Żywe wpisy po sprzątnięciu w elapsedNanos. observedAt jest tylko etykietą Clock
 * i może się cofać. Kolejność listy zawsze LRU -> MRU, także dla polityki LFU.
 * Widok nie zmienia się wraz z upływem czasu; wartości K/V nie są klonowane.
 */
public record CacheSnapshot<K, V>(int capacity, EvictionPolicy policy, Instant observedAt, long elapsedNanos,
        List<CacheItem<K, V>> leastToMostRecent, CacheStatistics statistics) {

    public CacheSnapshot {
        if (capacity < 1 || elapsedNanos < 0)
            throw new IllegalArgumentException("Niepoprawny stan");
        Objects.requireNonNull(policy);
        Objects.requireNonNull(observedAt);
        Objects.requireNonNull(statistics);
        leastToMostRecent = List.copyOf(leastToMostRecent);
        if (leastToMostRecent.size() > capacity)
            throw new IllegalArgumentException("Przekroczona pojemność");
    }
}
