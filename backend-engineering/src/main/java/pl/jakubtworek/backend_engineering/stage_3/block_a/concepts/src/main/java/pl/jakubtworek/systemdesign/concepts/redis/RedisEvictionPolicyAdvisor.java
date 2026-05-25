package pl.jakubtworek.backend_engineering.stage_3.block_a.concepts.src.main.java.pl.jakubtworek.systemdesign.concepts.redis;

/**
 * Selects a Redis eviction policy based on workload characteristics.
 */
public class RedisEvictionPolicyAdvisor {

    public RedisEvictionPolicy chooseForClassicCache(boolean stableHotKeys) {
        return stableHotKeys
                ? RedisEvictionPolicy.ALLKEYS_LFU
                : RedisEvictionPolicy.ALLKEYS_LRU;
    }

    public RedisEvictionPolicy chooseForTtlDrivenCache(boolean everyKeyHasTtl) {
        if (!everyKeyHasTtl) {
            throw new IllegalArgumentException("volatile-ttl is unsafe when many keys do not have TTL");
        }

        return RedisEvictionPolicy.VOLATILE_TTL;
    }

    public boolean isBadForClassicCache(RedisEvictionPolicy policy) {
        return policy == RedisEvictionPolicy.NOEVICTION;
    }
}