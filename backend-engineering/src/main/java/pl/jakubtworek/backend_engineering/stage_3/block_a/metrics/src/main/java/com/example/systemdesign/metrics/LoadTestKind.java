package pl.jakubtworek.backend_engineering.stage_3.block_a.metrics.src.main.java.com.example.systemdesign.metrics;

/**
 * Load test types used to validate a capacity hypothesis.
 */
public enum LoadTestKind {
    BASELINE,
    STEP,
    SPIKE,
    SOAK,
    CACHE_OFF,
    MISS_RATIO_UP,
    DEPENDENCY_FAILURE,
    RETRY_STORM
}
