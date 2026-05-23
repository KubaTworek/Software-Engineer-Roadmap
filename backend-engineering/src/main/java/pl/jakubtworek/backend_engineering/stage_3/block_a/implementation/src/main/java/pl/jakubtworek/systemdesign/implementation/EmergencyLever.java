package pl.jakubtworek.backend_engineering.stage_3.block_a.implementation.src.main.java.pl.jakubtworek.systemdesign.implementation;

/**
 * Operational levers used during incidents.
 *
 * These switches should be prepared and tested before an incident happens.
 */
public enum EmergencyLever {
    DISABLE_RECOMMENDATIONS,
    DISABLE_PERSONALIZATION,
    ENABLE_READ_ONLY_MODE,
    LIMIT_EXPENSIVE_ENDPOINTS,
    REDUCE_DEPENDENCY_CONCURRENCY,
    DISABLE_ENRICHMENT,
    SHED_LOW_PRIORITY_TRAFFIC,
    LOWER_RATE_LIMITS,
    SERVE_STALE_CACHE
}
