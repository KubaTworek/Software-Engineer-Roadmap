package pl.jakubtworek.backend_engineering.stage_3.block_a.implementation.src.main.java.pl.jakubtworek.systemdesign.implementation;

/**
 * Types of tests used to validate implementation assumptions.
 */
public enum LoadTestType {
    BASELINE,
    STEP,
    SPIKE,
    SOAK,
    CACHE_OFF,
    MISS_RATIO_UP,
    DEPENDENCY_FAILURE,
    RETRY_STORM,
    ROLLING_UPDATE,
    REPLICA_FAILURE
}
