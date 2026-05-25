package pl.jakubtworek.backend_engineering.stage_3.block_a.concepts.src.main.java.pl.jakubtworek.systemdesign.concepts.capacity;

/**
 * Performs practical capacity calculations.
 *
 * The goal is to predict the first bottleneck before running a load test.
 */
public final class CapacityCalculator {

    private CapacityCalculator() {
    }

    /**
     * Little's Law:
     * concurrency = RPS * latency_seconds
     */
    public static double concurrency(double rps, double latencySeconds) {
        if (rps < 0) throw new IllegalArgumentException("rps must be non-negative");
        if (latencySeconds < 0) throw new IllegalArgumentException("latencySeconds must be non-negative");

        return rps * latencySeconds;
    }

    /**
     * API CPU limit:
     * RPS_api = replicas * vCPU * target_utilization / CPU_seconds_per_request
     */
    public static double apiCpuLimitRps(CapacityPlan plan) {
        return plan.replicas()
                * plan.vCpuPerReplica()
                * plan.targetCpuUtilization()
                / plan.cpuSecondsPerRequest();
    }

    /**
     * Dependency pool limit:
     * RPS_dep = pool_size / (traffic_fraction * dependency_latency_seconds)
     */
    public static double dependencyPoolLimitRps(CapacityPlan plan) {
        return plan.dependencyPoolSize()
                / (plan.dependencyTrafficFraction() * plan.dependencyLatencySeconds());
    }

    /**
     * DB write limit:
     * RPS_db_write = DB_write_QPS_limit / (write_ratio * write_queries_per_request)
     */
    public static double dbWriteLimitRps(CapacityPlan plan) {
        return plan.dbWriteQpsLimit()
                / (plan.writeRatio() * plan.writeQueriesPerRequest());
    }
}