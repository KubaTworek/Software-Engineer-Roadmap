package pl.jakubtworek.backend_engineering.stage_3.block_a.metrics.src.main.java.com.example.systemdesign.metrics;

import java.util.List;

/**
 * Provides a conceptual table of Redis eviction policies.
 */
public final class RedisEvictionPolicyTable {

    private RedisEvictionPolicyTable() {
        // Utility class.
    }

    public static List<RedisEvictionPolicyComparison> defaultComparisons() {
        return List.of(
                new RedisEvictionPolicyComparison(
                        RedisEvictionPolicy.ALLKEYS_LRU,
                        "A small set of recently used hot keys drives most traffic.",
                        "Access frequency matters more than recency.",
                        "A safe default for a classic cache."
                ),
                new RedisEvictionPolicyComparison(
                        RedisEvictionPolicy.ALLKEYS_LFU,
                        "Hot keys remain stable over time.",
                        "Traffic patterns change rapidly in short windows.",
                        "Usually better for continuously hot keys."
                ),
                new RedisEvictionPolicyComparison(
                        RedisEvictionPolicy.VOLATILE_TTL,
                        "Keys consistently have TTL and shortest remaining TTL should be evicted first.",
                        "Many keys do not have TTL.",
                        "Works only with deliberate TTL design."
                ),
                new RedisEvictionPolicyComparison(
                        RedisEvictionPolicy.VOLATILE_LRU,
                        "Permanent and cache-like keys are mixed in the same Redis instance.",
                        "Cache and persistent roles can be separated into different Redis instances.",
                        "Separate instances are often cleaner than mixed semantics."
                ),
                new RedisEvictionPolicyComparison(
                        RedisEvictionPolicy.VOLATILE_LFU,
                        "TTL keys should be evicted based on approximate frequency.",
                        "Traffic is too volatile or many keys lack TTL.",
                        "Useful only when TTL discipline is strong."
                ),
                new RedisEvictionPolicyComparison(
                        RedisEvictionPolicy.NOEVICTION,
                        "Redis behaves as a store and data loss is unacceptable.",
                        "Redis is used as a classic cache that should degrade softly.",
                        "Writes fail when memory is full."
                )
        );
    }
}
