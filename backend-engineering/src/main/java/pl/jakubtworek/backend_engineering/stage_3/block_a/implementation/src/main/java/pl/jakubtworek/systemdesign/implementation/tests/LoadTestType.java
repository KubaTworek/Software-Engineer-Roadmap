package pl.jakubtworek.backend_engineering.stage_3.block_a.implementation.src.main.java.pl.jakubtworek.systemdesign.implementation.tests;

/**
 * Load test scenario types.
 */
public enum LoadTestType {
    BASELINE,
    STEP,
    SPIKE,
    SOAK,
    CACHE_OFF,
    MISS_RATIO_UP,
    DEPENDENCY_FAILURE,
    RETRY_STORM
}