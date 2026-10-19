package pl.jakubtworek.backend_engineering.stage_3.block_a.metrics.redis;

/**
 * Workload pattern observed in Redis.
 */
public enum RedisAccessPattern {
    RECENTLY_HOT_KEYS,
    STABLE_FREQUENT_HOT_KEYS,
    MIXED_PERSISTENT_AND_CACHE_KEYS,
    STRICT_TTL_DESIGN,
    STORE_NOT_CACHE
}