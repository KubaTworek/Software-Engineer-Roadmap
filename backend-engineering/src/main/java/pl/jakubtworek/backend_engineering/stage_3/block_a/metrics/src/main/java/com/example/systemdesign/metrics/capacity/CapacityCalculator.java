package pl.jakubtworek.backend_engineering.stage_3.block_a.metrics.src.main.java.com.example.systemdesign.metrics.capacity;

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
        return rps * latencySeconds;
    }

    /**
     * API CPU limit:
     * RPS_api = replicas * vCPU * target_utilization / CPU_seconds_per_request
     */
    public static double apiCpuLimitRps(CapacityInput input) {
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
        return input.dependencyPoolSize()
                / (input.dependencyTrafficFraction() * input.dependencyLatencySeconds());
    }

    /**
     * Database write limit:
     * RPS_db_write = DB_write_QPS_limit / (write_ratio * write_queries_per_request)
     */
    public static double dbWriteLimitRps(CapacityInput input) {
        return input.dbWriteQpsLimit()
                / (input.writeRatio() * input.writeQueriesPerRequest());
    }

    /**
     * Database read QPS after cache:
     * DB_read_QPS = RPS * miss_ratio * queries_on_miss
     */
    public static double dbReadQpsAfterCache(double rps, CapacityInput input) {
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
        if (rps <= 0) {
            throw new IllegalArgumentException("rps must be positive");
        }

        return replicas * vCpuPerReplica * averageCpuUtilization / rps;
    }
}