package pl.jakubtworek.backend_engineering.stage_3.block_a.concepts.src.main.java.pl.jakubtworek.systemdesign.concepts.degradation;

/**
 * Operational levers that can be toggled during incidents.
 */
public enum EmergencyLever {
    DISABLE_RECOMMENDATIONS,
    DISABLE_PERSONALIZATION,
    ENABLE_READ_ONLY_MODE,
    LIMIT_EXPENSIVE_ENDPOINTS,
    REDUCE_DEPENDENCY_CONCURRENCY,
    DISABLE_ENRICHMENT,
    SHED_LOW_PRIORITY_TRAFFIC,
    SERVE_STALE_CACHE
}