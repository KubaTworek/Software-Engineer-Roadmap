package pl.jakubtworek.lld.cache.domain;

import java.time.Duration;
import java.util.Objects;
import java.util.Optional;

/**
 * Wpis widoku: liczba użyć i pozostały TTL w chwili snapshotu, nie data UTC expiry.
 * Optional.empty oznacza bezterminowość. Kopia listy nie klonuje generycznych K/V.
 */
public record CacheItem<K, V>(K key, V value, long frequency, Optional<Duration> remainingTtl) {

    public CacheItem {
        Objects.requireNonNull(key);
        Objects.requireNonNull(value);
        Objects.requireNonNull(remainingTtl);
        if (frequency < 1 || remainingTtl.filter(d -> d.isNegative() || d.isZero()).isPresent()) {
            throw new IllegalArgumentException("Niepoprawne metadane żywego wpisu");
        }
    }
}
