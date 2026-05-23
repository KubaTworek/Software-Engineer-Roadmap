package pl.jakubtworek.backend_engineering.stage_3.block_a.concepts.src.main.java.pl.jakubtworek.systemdesign;

import java.time.Duration;

/**
 * Describes cache behavior for read-heavy data.
 *
 * TTL should be selected based on two questions:
 * - how fresh the data must be
 * - how expensive a cache miss is
 */
public record CachePolicy(
        Duration ttl,
        Duration jitter,
        EvictionPolicy evictionPolicy,
        boolean staleWhileRevalidateEnabled,
        boolean singleFlightEnabled
) {
    public CachePolicy {
        if (ttl == null || ttl.isNegative() || ttl.isZero()) {
            throw new IllegalArgumentException("ttl must be positive");
        }
        if (jitter == null || jitter.isNegative()) {
            throw new IllegalArgumentException("jitter must be non-negative");
        }
        if (evictionPolicy == null) {
            throw new IllegalArgumentException("evictionPolicy is required");
        }
    }
}
