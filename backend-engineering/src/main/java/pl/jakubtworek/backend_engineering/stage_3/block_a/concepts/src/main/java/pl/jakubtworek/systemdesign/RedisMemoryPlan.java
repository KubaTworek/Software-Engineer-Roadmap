package pl.jakubtworek.backend_engineering.stage_3.block_a.concepts.src.main.java.pl.jakubtworek.systemdesign;

/**
 * Models a safe Redis maxmemory calculation.
 *
 * Redis memory used by replication and persistence buffers may not be counted
 * for eviction. A production configuration should leave enough headroom for
 * these buffers and for the operating system.
 */
public record RedisMemoryPlan(
        long availableRamBytes,
        long memoryNotCountedForEvictionBytes,
        long operatingSystemReserveBytes
) {
    public RedisMemoryPlan {
        if (availableRamBytes <= 0) {
            throw new IllegalArgumentException("availableRamBytes must be positive");
        }
        if (memoryNotCountedForEvictionBytes < 0) {
            throw new IllegalArgumentException("memoryNotCountedForEvictionBytes must be non-negative");
        }
        if (operatingSystemReserveBytes < 0) {
            throw new IllegalArgumentException("operatingSystemReserveBytes must be non-negative");
        }
    }

    /**
     * Recommended maxmemory:
     *
     * maxmemory = available RAM
     *             - memory not counted for eviction
     *             - operating system reserve
     */
    public long recommendedMaxMemoryBytes() {
        long result = availableRamBytes - memoryNotCountedForEvictionBytes - operatingSystemReserveBytes;
        if (result <= 0) {
            throw new IllegalStateException("No safe memory left for Redis maxmemory");
        }
        return result;
    }
}
