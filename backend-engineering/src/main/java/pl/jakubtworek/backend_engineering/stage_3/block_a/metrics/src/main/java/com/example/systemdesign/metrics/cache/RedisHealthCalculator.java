package pl.jakubtworek.backend_engineering.stage_3.block_a.metrics.src.main.java.com.example.systemdesign.metrics.cache;

/**
 * Calculates Redis cache health indicators.
 */
public final class RedisHealthCalculator {

    private RedisHealthCalculator() {
    }

    /**
     * hit_ratio = keyspace_hits / (keyspace_hits + keyspace_misses)
     */
    public static double hitRatio(RedisCounters counters) {
        long total = counters.keyspaceHits() + counters.keyspaceMisses();

        if (total == 0) {
            return 1.0;
        }

        return (double) counters.keyspaceHits() / total;
    }

    public static double missRatio(RedisCounters counters) {
        return 1.0 - hitRatio(counters);
    }

    public static double usedMemoryRatio(RedisCounters counters) {
        return (double) counters.usedMemoryBytes() / counters.maxMemoryBytes();
    }

    public static boolean hasContinuousEvictions(RedisCounters counters) {
        return counters.evictedKeys() > 0;
    }

    public static boolean memoryCloseToLimit(RedisCounters counters) {
        return usedMemoryRatio(counters) > 0.90;
    }

    public static boolean memoryNotCountedForEvictionIsGrowingRisk(RedisCounters counters) {
        return counters.memoryNotCountedForEvictionBytes() > counters.maxMemoryBytes() * 0.10;
    }
}