package pl.jakubtworek.backend_engineering.stage_3.block_a.concepts.src.main.java.pl.jakubtworek.systemdesign.concepts.redis;

/**
 * Runtime Redis memory plan.
 *
 * maxmemory should leave room for memory not counted for eviction
 * and for the operating system.
 */
public record RedisMemoryPlan(
        long availableRamBytes,
        long memoryNotCountedForEvictionBytes,
        long operatingSystemReserveBytes
) {
    public RedisMemoryPlan {
        if (availableRamBytes <= 0) throw new IllegalArgumentException("availableRamBytes must be positive");
        if (memoryNotCountedForEvictionBytes < 0) throw new IllegalArgumentException("memoryNotCountedForEvictionBytes must be non-negative");
        if (operatingSystemReserveBytes < 0) throw new IllegalArgumentException("operatingSystemReserveBytes must be non-negative");
    }

    /**
     * maxmemory ≈ available RAM - mem_not_counted_for_evict - OS reserve
     */
    public long recommendedMaxMemoryBytes() {
        long result = availableRamBytes
                - memoryNotCountedForEvictionBytes
                - operatingSystemReserveBytes;

        if (result <= 0) {
            throw new IllegalStateException("No safe memory left for Redis maxmemory");
        }

        return result;
    }
}