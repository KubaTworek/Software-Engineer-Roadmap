package pl.jakubtworek.backend_engineering.stage_3.block_a.concepts.src.main.java.pl.jakubtworek.systemdesign.concepts.capacity;

/**
 * Input used to calculate the first expected bottleneck on a request path.
 *
 * This models the request path as a sequence of limited resources:
 * API CPU, dependency pool, and database writes.
 */
public record CapacityPlan(
        int replicas,
        double vCpuPerReplica,
        double targetCpuUtilization,
        double cpuSecondsPerRequest,

        int dependencyPoolSize,
        double dependencyTrafficFraction,
        double dependencyLatencySeconds,

        double dbWriteQpsLimit,
        double writeRatio,
        int writeQueriesPerRequest
) {
    public CapacityPlan {
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
    }
}