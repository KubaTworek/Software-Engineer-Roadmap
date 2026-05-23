package pl.jakubtworek.backend_engineering.stage_3.block_a.implementation.src.main.java.pl.jakubtworek.systemdesign.implementation;

/**
 * Describes how cached data is handled after writes to the source of truth.
 */
public enum CacheInvalidationMode {
    INVALIDATE_ON_WRITE,
    REFRESH_ON_WRITE,
    TTL_ONLY,
    WRITE_THROUGH
}
