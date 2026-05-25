package pl.jakubtworek.backend_engineering.stage_3.block_a.metrics.src.main.java.com.example.systemdesign.metrics.capacity;

/**
 * Input parameters for request path capacity analysis.
 *
 * This describes one critical request path:
 * API CPU, external dependency pool, DB writes, and DB reads after cache.
 */
public record CapacityInput(
        int replicas,
        double vCpuPerReplica,
        double targetCpuUtilization,
        double cpuSecondsPerRequest,
        int dependencyPoolSize,
        double dependencyTrafficFraction,
        double dependencyLatencySeconds,
        double dbWriteQpsLimit,
        double writeRatio,
        int writeQueriesPerRequest,
        double cacheMissRatio,
        int readQueriesOnMiss
) {
    public CapacityInput {
        if (replicas <= 0) throw new IllegalArgumentException("replicas must be positive");
        if (vCpuPerReplica <= 0) throw new IllegalArgumentException("vCpuPerReplica must be positive");
        if (targetCpuUtilization <= 0 || targetCpuUtilization > 1) throw new IllegalArgumentException("targetCpuUtilization must be in range (0, 1]");
        if (cpuSecondsPerRequest <= 0) throw new IllegalArgumentException("cpuSecondsPerRequest must be positive");
        if (dependencyPoolSize <= 0) throw new IllegalArgumentException("dependencyPoolSize must be positive");
        if (dependencyTrafficFraction <= 0 || dependencyTrafficFraction > 1) throw new IllegalArgumentException("dependencyTrafficFraction must be in range (0, 1]");
        if (dependencyLatencySeconds <= 0) throw new IllegalArgumentException("dependencyLatencySeconds must be positive");
        if (dbWriteQpsLimit <= 0) throw new IllegalArgumentException("dbWriteQpsLimit must be positive");
        if (writeRatio <= 0 || writeRatio > 1) throw new IllegalArgumentException("writeRatio must be in range (0, 1]");
        if (writeQueriesPerRequest <= 0) throw new IllegalArgumentException("writeQueriesPerRequest must be positive");
        if (cacheMissRatio < 0 || cacheMissRatio > 1) throw new IllegalArgumentException("cacheMissRatio must be in range [0, 1]");
        if (readQueriesOnMiss < 0) throw new IllegalArgumentException("readQueriesOnMiss must be non-negative");
    }
}