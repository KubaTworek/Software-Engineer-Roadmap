package pl.jakubtworek.backend_engineering.stage_3.block_a.concepts.src.main.java.pl.jakubtworek.systemdesign;

/**
 * Emergency lever used during incidents.
 *
 * These are operational controls that allow the system to degrade gracefully
 * without deploying new code.
 */
public enum OperationalLever {
    DISABLE_RECOMMENDATIONS,
    DISABLE_PERSONALIZATION,
    ENABLE_READ_ONLY_MODE,
    LIMIT_EXPENSIVE_ENDPOINTS,
    REDUCE_DEPENDENCY_CONCURRENCY,
    DISABLE_ENRICHMENT,
    SHED_LOW_PRIORITY_TRAFFIC
}
