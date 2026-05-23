package pl.jakubtworek.backend_engineering.stage_3.block_a.metrics.src.main.java.com.example.systemdesign.metrics;

/**
 * Implements the minimal formulas needed to reason about system bottlenecks.
 *
 * These formulas are intentionally simple. Their purpose is to identify
 * the approximate knee of the latency curve and the likely first bottleneck.
 */
public final class CapacityFormula {

    private CapacityFormula() {
        // Utility class.
    }

    /**
     * Little's Law approximation:
     *
     * concurrency = RPS * latency_seconds
     */
    public static double concurrency(double rps, double latencySeconds) {
        requireNonNegative(rps, "rps");
        requireNonNegative(latencySeconds, "latencySeconds");
        return rps * latencySeconds;
    }

    /**
     * API CPU limit:
     *
     * RPS_api = replicas * vCPU * target_utilization / CPU_seconds_per_request
     */
    public static double apiCpuLimitRps(
            int replicas,
            double vCpuPerReplica,
            double targetUtilization,
            double cpuSecondsPerRequest
    ) {
        if (replicas <= 0) throw new IllegalArgumentException("replicas must be positive");
        requirePositive(vCpuPerReplica, "vCpuPerReplica");
        requirePositive(cpuSecondsPerRequest, "cpuSecondsPerRequest");
        if (targetUtilization <= 0 || targetUtilization > 1) {
            throw new IllegalArgumentException("targetUtilization must be in range (0, 1]");
        }

        return replicas * vCpuPerReplica * targetUtilization / cpuSecondsPerRequest;
    }

    /**
     * Dependency pool limit:
     *
     * RPS_dep = pool_size / (traffic_fraction * dependency_latency_seconds)
     */
    public static double dependencyPoolLimitRps(
            int poolSize,
            double trafficFraction,
            double dependencyLatencySeconds
    ) {
        if (poolSize <= 0) throw new IllegalArgumentException("poolSize must be positive");
        if (trafficFraction <= 0 || trafficFraction > 1) {
            throw new IllegalArgumentException("trafficFraction must be in range (0, 1]");
        }
        requirePositive(dependencyLatencySeconds, "dependencyLatencySeconds");

        return poolSize / (trafficFraction * dependencyLatencySeconds);
    }

    /**
     * Database write limit:
     *
     * RPS_db_write = DB_write_QPS_limit / (write_ratio * write_queries_per_request)
     */
    public static double databaseWriteLimitRps(
            double databaseWriteQpsLimit,
            double writeRatio,
            int writeQueriesPerRequest
    ) {
        requirePositive(databaseWriteQpsLimit, "databaseWriteQpsLimit");
        if (writeRatio <= 0 || writeRatio > 1) {
            throw new IllegalArgumentException("writeRatio must be in range (0, 1]");
        }
        if (writeQueriesPerRequest <= 0) {
            throw new IllegalArgumentException("writeQueriesPerRequest must be positive");
        }

        return databaseWriteQpsLimit / (writeRatio * writeQueriesPerRequest);
    }

    /**
     * Database read load after cache:
     *
     * DB_read_QPS = RPS * miss_ratio * queries_on_miss
     */
    public static double databaseReadQpsAfterCache(
            double rps,
            double missRatio,
            int queriesOnMiss
    ) {
        requireNonNegative(rps, "rps");
        if (missRatio < 0 || missRatio > 1) {
            throw new IllegalArgumentException("missRatio must be in range [0, 1]");
        }
        if (queriesOnMiss < 0) {
            throw new IllegalArgumentException("queriesOnMiss must be non-negative");
        }

        return rps * missRatio * queriesOnMiss;
    }

    /**
     * Estimates CPU seconds per request from a stable load test point:
     *
     * CPU_s_per_request = replicas * vCPU * avg_CPU_utilization / RPS
     */
    public static double estimateCpuSecondsPerRequest(
            int replicas,
            double vCpuPerReplica,
            double averageCpuUtilization,
            double rps
    ) {
        if (replicas <= 0) throw new IllegalArgumentException("replicas must be positive");
        requirePositive(vCpuPerReplica, "vCpuPerReplica");
        if (averageCpuUtilization < 0 || averageCpuUtilization > 1) {
            throw new IllegalArgumentException("averageCpuUtilization must be in range [0, 1]");
        }
        requirePositive(rps, "rps");

        return replicas * vCpuPerReplica * averageCpuUtilization / rps;
    }

    private static void requirePositive(double value, String name) {
        if (value <= 0) throw new IllegalArgumentException(name + " must be positive");
    }

    private static void requireNonNegative(double value, String name) {
        if (value < 0) throw new IllegalArgumentException(name + " must be non-negative");
    }
}
