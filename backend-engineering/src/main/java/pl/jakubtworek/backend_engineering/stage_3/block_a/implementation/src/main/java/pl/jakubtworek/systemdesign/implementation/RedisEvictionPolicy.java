package pl.jakubtworek.backend_engineering.stage_3.block_a.implementation.src.main.java.pl.jakubtworek.systemdesign.implementation;

/**
 * Redis-like eviction policies used when maxmemory is reached.
 */
public enum RedisEvictionPolicy {
    ALLKEYS_LRU,
    ALLKEYS_LFU,
    VOLATILE_TTL,
    VOLATILE_LRU,
    VOLATILE_LFU,
    NOEVICTION
}
