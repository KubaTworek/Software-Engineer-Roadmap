package pl.jakubtworek.backend_engineering.stage_3.block_a.metrics.src.main.java.com.example.systemdesign.metrics;

/**
 * Describes when a Redis eviction policy should be considered.
 *
 * The correct policy depends on whether Redis behaves like a cache,
 * a store, or a mixed workload instance.
 */
public record RedisEvictionPolicyComparison(
        RedisEvictionPolicy policy,
        String useWhen,
        String avoidWhen,
        String practicalNote
) {
    public RedisEvictionPolicyComparison {
        if (policy == null) {
            throw new IllegalArgumentException("Policy is required");
        }
        requireText(useWhen, "useWhen");
        requireText(avoidWhen, "avoidWhen");
        requireText(practicalNote, "practicalNote");
    }

    private static void requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " is required");
        }
    }
}
