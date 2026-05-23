package pl.jakubtworek.backend_engineering.stage_3.block_a.implementation.src.main.java.pl.jakubtworek.systemdesign.implementation;

/**
 * Strategies that reduce cache stampede risk.
 */
public enum StampedeProtectionStrategy {
    SINGLE_FLIGHT,
    REQUEST_COALESCING,
    STALE_WHILE_REVALIDATE,
    TTL_JITTER,
    BACKGROUND_REFRESH,
    LOCK_PER_HOT_KEY
}
