package pl.jakubtworek.backend_engineering.stage_3.block_a.metrics.src.main.java.com.example.systemdesign.metrics;

/**
 * Describes the critical request path used for capacity modeling.
 *
 * These values do not need to be perfect at the beginning.
 * They create a testable hypothesis about the first bottleneck.
 */
public record RequestPathProfile(
        int replicas,
        double vCpuPerReplica,
        double targetCpuUtilization,
        double cpuSecondsPerRequest,
        int dependencyPoolSize,
        double dependencyLatencySeconds,
        double dependencyTrafficFraction,
        double databaseWriteQpsLimit,
        double writeRatio,
        int writeQueriesPerRequest,
        double cacheMissRatio,
        int readQueriesOnCacheMiss
) {
    public RequestPathProfile {
        if (replicas <= 0) throw new IllegalArgumentException("replicas must be positive");
        if (vCpuPerReplica <= 0) throw new IllegalArgumentException("vCpuPerReplica must be positive");
        if (targetCpuUtilization <= 0 || targetCpuUtilization > 1) throw new IllegalArgumentException("targetCpuUtilization must be in range (0, 1]");
        if (cpuSecondsPerRequest <= 0) throw new IllegalArgumentException("cpuSecondsPerRequest must be positive");
        if (dependencyPoolSize <= 0) throw new IllegalArgumentException("dependencyPoolSize must be positive");
        if (dependencyLatencySeconds <= 0) throw new IllegalArgumentException("dependencyLatencySeconds must be positive");
        if (dependencyTrafficFraction <= 0 || dependencyTrafficFraction > 1) throw new IllegalArgumentException("dependencyTrafficFraction must be in range (0, 1]");
        if (databaseWriteQpsLimit <= 0) throw new IllegalArgumentException("databaseWriteQpsLimit must be positive");
        if (writeRatio <= 0 || writeRatio > 1) throw new IllegalArgumentException("writeRatio must be in range (0, 1]");
        if (writeQueriesPerRequest <= 0) throw new IllegalArgumentException("writeQueriesPerRequest must be positive");
        if (cacheMissRatio < 0 || cacheMissRatio > 1) throw new IllegalArgumentException("cacheMissRatio must be in range [0, 1]");
        if (readQueriesOnCacheMiss < 0) throw new IllegalArgumentException("readQueriesOnCacheMiss must be non-negative");
    }
}
