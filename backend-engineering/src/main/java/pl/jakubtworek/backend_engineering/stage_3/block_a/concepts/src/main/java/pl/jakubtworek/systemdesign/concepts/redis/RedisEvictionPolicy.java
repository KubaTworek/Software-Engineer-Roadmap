package pl.jakubtworek.backend_engineering.stage_3.block_a.concepts.src.main.java.pl.jakubtworek.systemdesign.concepts.redis;

/**
 * Redis eviction policies commonly used for cache design.
 */
public enum RedisEvictionPolicy {
    ALLKEYS_LRU,
    ALLKEYS_LFU,
    VOLATILE_TTL,
    VOLATILE_LRU,
    VOLATILE_LFU,
    NOEVICTION
}