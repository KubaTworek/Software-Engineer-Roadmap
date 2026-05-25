package pl.jakubtworek.backend_engineering.stage_3.block_b.tracing;

/**
 * Represents the result of a cache lookup from a tracing perspective.
 *
 * This is intentionally low-cardinality: hit, miss, error, or timeout
 * are useful attributes; cache keys are not.
 */
public enum CacheTraceResult {

    HIT(true),
    MISS(false);

    private final boolean cacheHit;

    CacheTraceResult(boolean cacheHit) {
        this.cacheHit = cacheHit;
    }

    public boolean cacheHit() {
        return cacheHit;
    }
}