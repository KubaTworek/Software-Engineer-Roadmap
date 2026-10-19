package pl.jakubtworek.backend_engineering.stage_3.block_a.metrics.redis;

/**
 * Supported Redis eviction policies.
 */
public enum RedisEvictionPolicy {
    ALLKEYS_LRU,
    ALLKEYS_LFU,
    VOLATILE_TTL,
    VOLATILE_LRU,
    VOLATILE_LFU,
    NOEVICTION
}