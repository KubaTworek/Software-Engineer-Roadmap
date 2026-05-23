package pl.jakubtworek.backend_engineering.stage_3.block_a.implementation.src.main.java.pl.jakubtworek.systemdesign.implementation;

/**
 * Metrics commonly observed during scalability and resilience tests.
 */
public enum TestMetric {
    RPS,
    P50_LATENCY,
    P95_LATENCY,
    P99_LATENCY,
    CPU,
    MEMORY,
    GC_PAUSE,
    DB_QPS,
    DB_LATENCY,
    POOL_WAIT,
    CACHE_HIT_RATIO,
    CACHE_MISS_RATIO,
    DEPENDENCY_LATENCY,
    TIMEOUT_COUNT,
    RETRY_COUNT,
    RETRY_AMPLIFICATION,
    BREAKER_OPEN_RATE,
    FALLBACK_COUNT,
    QUEUE_DEPTH,
    OLDEST_MESSAGE_AGE,
    DLQ_VOLUME,
    ERROR_RATE,
    RATE_LIMIT_429
}
