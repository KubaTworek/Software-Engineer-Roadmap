package pl.jakubtworek.backend_engineering.stage_3.block_a.implementation.tests;

/**
 * Load test scenario types.
 */
public enum LoadTestType {
    BASELINE,
    LOAD,
    STEP,
    STRESS,
    SPIKE,
    SOAK,
    CACHE_OFF,
    MISS_RATIO_UP,
    DEPENDENCY_FAILURE,
    RETRY_STORM
}
