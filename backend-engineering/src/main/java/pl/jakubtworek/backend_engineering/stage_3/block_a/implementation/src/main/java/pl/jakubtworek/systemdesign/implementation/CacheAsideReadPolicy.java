package pl.jakubtworek.backend_engineering.stage_3.block_a.implementation.src.main.java.pl.jakubtworek.systemdesign.implementation;

import java.time.Duration;

/**
 * Describes a cache-aside policy for a class of read data.
 *
 * TTL should be chosen based on freshness requirements and the cost of a cache miss.
 */
public record CacheAsideReadPolicy(
        String dataClass,
        DataFreshnessClass freshnessClass,
        Duration ttl,
        CacheInvalidationMode invalidationMode,
        boolean cacheMissIsExpensive
) {
    public CacheAsideReadPolicy {
        requireText(dataClass, "dataClass");
        if (freshnessClass == null) {
            throw new IllegalArgumentException("freshnessClass is required");
        }
        if (ttl == null || ttl.isNegative() || ttl.isZero()) {
            throw new IllegalArgumentException("ttl must be positive");
        }
        if (invalidationMode == null) {
            throw new IllegalArgumentException("invalidationMode is required");
        }
    }

    /**
     * TTL-only invalidation is risky when data must be strictly fresh.
     */
    public boolean hasFreshnessRisk() {
        return freshnessClass == DataFreshnessClass.STRICTLY_FRESH
                && invalidationMode == CacheInvalidationMode.TTL_ONLY;
    }

    private static void requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " is required");
        }
    }
}
