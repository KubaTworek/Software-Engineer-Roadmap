package pl.jakubtworek.backend_engineering.stage_3.block_a.metrics.src.main.java.com.example.systemdesign.metrics.redis;

/**
 * Chooses a Redis eviction policy based on the observed access pattern.
 *
 * This is not a replacement for production validation.
 * It is a practical starting policy advisor.
 */
public class RedisEvictionPolicyAdvisor {

    public RedisEvictionPolicy recommend(RedisAccessPattern pattern) {
        return switch (pattern) {
            case RECENTLY_HOT_KEYS -> RedisEvictionPolicy.ALLKEYS_LRU;
            case STABLE_FREQUENT_HOT_KEYS -> RedisEvictionPolicy.ALLKEYS_LFU;
            case STRICT_TTL_DESIGN -> RedisEvictionPolicy.VOLATILE_TTL;
            case MIXED_PERSISTENT_AND_CACHE_KEYS -> RedisEvictionPolicy.VOLATILE_LRU;
            case STORE_NOT_CACHE -> RedisEvictionPolicy.NOEVICTION;
        };
    }

    public String explain(RedisEvictionPolicy policy) {
        return switch (policy) {
            case ALLKEYS_LRU ->
                    "Good default for classic cache when recently used hot keys dominate traffic.";
            case ALLKEYS_LFU ->
                    "Useful when hot keys are stable and access frequency matters more than recency.";
            case VOLATILE_TTL ->
                    "Useful only when TTL is consistently assigned and shortest TTL should be evicted first.";
            case VOLATILE_LRU, VOLATILE_LFU ->
                    "Useful when persistent and cache-like keys are mixed, but separate Redis instances are often cleaner.";
            case NOEVICTION ->
                    "Suitable when Redis is a store, not a cache. Risky for cache because writes fail when memory is full.";
        };
    }
}