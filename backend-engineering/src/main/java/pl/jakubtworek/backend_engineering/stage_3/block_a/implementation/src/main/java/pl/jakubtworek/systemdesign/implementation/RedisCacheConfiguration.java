package pl.jakubtworek.backend_engineering.stage_3.block_a.implementation.src.main.java.pl.jakubtworek.systemdesign.implementation;

/**
 * Describes Redis memory and eviction configuration.
 *
 * maxmemory should leave headroom for replication buffers, persistence buffers,
 * memory not counted for eviction, and the operating system.
 */
public record RedisCacheConfiguration(
        long availableRamBytes,
        long memoryNotCountedForEvictionBytes,
        long operatingSystemReserveBytes,
        RedisEvictionPolicy evictionPolicy
) {
    public RedisCacheConfiguration {
        if (availableRamBytes <= 0) {
            throw new IllegalArgumentException("availableRamBytes must be positive");
        }
        if (memoryNotCountedForEvictionBytes < 0) {
            throw new IllegalArgumentException("memoryNotCountedForEvictionBytes must be non-negative");
        }
        if (operatingSystemReserveBytes < 0) {
            throw new IllegalArgumentException("operatingSystemReserveBytes must be non-negative");
        }
        if (evictionPolicy == null) {
            throw new IllegalArgumentException("evictionPolicy is required");
        }
    }

    /**
     * Recommended maxmemory:
     * available RAM minus Redis memory not counted for eviction minus OS reserve.
     */
    public long recommendedMaxMemoryBytes() {
        long value = availableRamBytes - memoryNotCountedForEvictionBytes - operatingSystemReserveBytes;
        if (value <= 0) {
            throw new IllegalStateException("No safe memory remains for Redis maxmemory");
        }
        return value;
    }

    /**
     * noeviction is usually a weak choice for a classic cache because writes fail when memory is full.
     */
    public boolean isRiskyForClassicCache() {
        return evictionPolicy == RedisEvictionPolicy.NOEVICTION;
    }
}
