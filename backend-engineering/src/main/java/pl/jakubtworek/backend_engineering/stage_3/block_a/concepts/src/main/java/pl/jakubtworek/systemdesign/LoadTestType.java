package pl.jakubtworek.backend_engineering.stage_3.block_a.concepts.src.main.java.pl.jakubtworek.systemdesign;

/**
 * Types of tests used to validate the capacity model.
 */
public enum LoadTestType {
    BASELINE,
    STEP,
    SPIKE,
    SOAK,
    CACHE_OFF,
    DEPENDENCY_FAILURE,
    RETRY_STORM
}
