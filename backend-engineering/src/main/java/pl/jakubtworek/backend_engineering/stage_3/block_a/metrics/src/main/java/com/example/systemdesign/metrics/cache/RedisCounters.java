package pl.jakubtworek.backend_engineering.stage_3.block_a.metrics.src.main.java.com.example.systemdesign.metrics.cache;

/**
 * Runtime Redis counters used to calculate cache effectiveness.
 */
public record RedisCounters(
        long keyspaceHits,
        long keyspaceMisses,
        long evictedKeys,
        long expiredKeys,
        long usedMemoryBytes,
        long maxMemoryBytes,
        long memoryNotCountedForEvictionBytes
) {
    public RedisCounters {
        if (keyspaceHits < 0) throw new IllegalArgumentException("keyspaceHits must be non-negative");
        if (keyspaceMisses < 0) throw new IllegalArgumentException("keyspaceMisses must be non-negative");
        if (evictedKeys < 0) throw new IllegalArgumentException("evictedKeys must be non-negative");
        if (expiredKeys < 0) throw new IllegalArgumentException("expiredKeys must be non-negative");
        if (usedMemoryBytes < 0) throw new IllegalArgumentException("usedMemoryBytes must be non-negative");
        if (maxMemoryBytes <= 0) throw new IllegalArgumentException("maxMemoryBytes must be positive");
        if (memoryNotCountedForEvictionBytes < 0) throw new IllegalArgumentException("memoryNotCountedForEvictionBytes must be non-negative");
    }
}