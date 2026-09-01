package pl.jakubtworek.backend_engineering.stage_3.block_a.metrics.capacity;

import java.util.Objects;

/**
 * Capacity formulas used for first-order bottleneck prediction.
 *
 * The purpose is not perfect simulation.
 * The purpose is to predict the first likely bottleneck and validate it with load tests.
 */
public final class CapacityCalculator {

    private CapacityCalculator() {
    }

    /**
     * Little's Law approximation:
     * concurrency = throughput * latency
     */
    public static double concurrency(double rps, double latencySeconds) {
        if (!Double.isFinite(rps) || rps < 0) throw new IllegalArgumentException("rps must be finite and non-negative");
        if (!Double.isFinite(latencySeconds) || latencySeconds < 0) throw new IllegalArgumentException("latencySeconds must be finite and non-negative");
        return rps * latencySeconds;
    }

    /**
     * API CPU limit:
     * RPS_api = replicas * vCPU * target_utilization / CPU_seconds_per_request
     */
    public static double apiCpuLimitRps(CapacityInput input) {
        Objects.requireNonNull(input, "input must not be null");
        return input.replicas()
                * input.vCpuPerReplica()
                * input.targetCpuUtilization()
                / input.cpuSecondsPerRequest();
    }

    /**
     * Dependency pool limit:
     * RPS_dep = pool_size / (traffic_fraction * dependency_latency_seconds)
     */
    public static double dependencyPoolLimitRps(CapacityInput input) {
        Objects.requireNonNull(input, "input must not be null");
        if (input.dependencyTrafficFraction() == 0) {
            return Double.POSITIVE_INFINITY;
        }
        return input.dependencyPoolSize()
                / (input.dependencyTrafficFraction() * input.dependencyLatencySeconds());
    }

    /**
     * Database write limit:
     * RPS_db_write = DB_write_QPS_limit / (write_ratio * write_queries_per_request)
     */
    public static double dbWriteLimitRps(CapacityInput input) {
        Objects.requireNonNull(input, "input must not be null");
        if (input.writeRatio() == 0 || input.writeQueriesPerRequest() == 0) {
            return Double.POSITIVE_INFINITY;
        }
        return input.dbWriteQpsLimit()
                / (input.writeRatio() * input.writeQueriesPerRequest());
    }

    /**
     * Database read QPS after cache:
     * DB_read_QPS = RPS * miss_ratio * queries_on_miss
     */
    public static double dbReadQpsAfterCache(double rps, CapacityInput input) {
        if (!Double.isFinite(rps) || rps < 0) {
            throw new IllegalArgumentException("rps must be finite and non-negative");
        }
        Objects.requireNonNull(input, "input must not be null");
        return rps * input.cacheMissRatio() * input.readQueriesOnMiss();
    }

    /**
     * Estimate CPU seconds per request from a stable load test:
     * CPU_s_per_request = replicas * vCPU * avg_CPU_utilization / RPS
     */
    public static double estimateCpuSecondsPerRequest(
            int replicas,
            double vCpuPerReplica,
            double averageCpuUtilization,
            double rps
    ) {
        if (replicas <= 0) throw new IllegalArgumentException("replicas must be positive");
        if (!Double.isFinite(vCpuPerReplica) || vCpuPerReplica <= 0) throw new IllegalArgumentException("vCpuPerReplica must be finite and positive");
        if (!Double.isFinite(averageCpuUtilization) || averageCpuUtilization < 0 || averageCpuUtilization > 1) throw new IllegalArgumentException("averageCpuUtilization must be finite and in range [0, 1]");
        if (!Double.isFinite(rps) || rps <= 0) throw new IllegalArgumentException("rps must be finite and positive");

        return replicas * vCpuPerReplica * averageCpuUtilization / rps;
    }
}
