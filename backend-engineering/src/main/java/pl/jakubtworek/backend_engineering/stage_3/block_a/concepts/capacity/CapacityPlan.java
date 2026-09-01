package pl.jakubtworek.backend_engineering.stage_3.block_a.concepts.capacity;

/**
 * Input used to calculate the first expected bottleneck on a request path.
 *
 * This models the request path as a sequence of limited resources. Zero usage
 * means that a given resource is not part of this request path.
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
        if (!Double.isFinite(vCpuPerReplica) || vCpuPerReplica <= 0) throw new IllegalArgumentException("vCpuPerReplica must be finite and positive");
        if (!Double.isFinite(targetCpuUtilization) || targetCpuUtilization <= 0 || targetCpuUtilization > 1) throw new IllegalArgumentException("targetCpuUtilization must be finite and in range (0, 1]");
        if (!Double.isFinite(cpuSecondsPerRequest) || cpuSecondsPerRequest <= 0) throw new IllegalArgumentException("cpuSecondsPerRequest must be finite and positive");

        if (dependencyPoolSize <= 0) throw new IllegalArgumentException("dependencyPoolSize must be positive");
        if (!Double.isFinite(dependencyTrafficFraction) || dependencyTrafficFraction < 0 || dependencyTrafficFraction > 1) throw new IllegalArgumentException("dependencyTrafficFraction must be finite and in range [0, 1]");
        if (!Double.isFinite(dependencyLatencySeconds) || dependencyLatencySeconds <= 0) throw new IllegalArgumentException("dependencyLatencySeconds must be finite and positive");

        if (!Double.isFinite(dbWriteQpsLimit) || dbWriteQpsLimit <= 0) throw new IllegalArgumentException("dbWriteQpsLimit must be finite and positive");
        if (!Double.isFinite(writeRatio) || writeRatio < 0 || writeRatio > 1) throw new IllegalArgumentException("writeRatio must be finite and in range [0, 1]");
        if (writeQueriesPerRequest < 0) throw new IllegalArgumentException("writeQueriesPerRequest must be non-negative");
    }
}
