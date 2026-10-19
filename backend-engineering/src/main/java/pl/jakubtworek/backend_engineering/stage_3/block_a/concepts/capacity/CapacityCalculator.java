package pl.jakubtworek.backend_engineering.stage_3.block_a.concepts.capacity;

import java.util.Objects;

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
        if (!Double.isFinite(rps) || rps < 0) throw new IllegalArgumentException("rps must be finite and non-negative");
        if (!Double.isFinite(latencySeconds) || latencySeconds < 0) throw new IllegalArgumentException("latencySeconds must be finite and non-negative");

        return rps * latencySeconds;
    }

    /**
     * API CPU limit:
     * RPS_api = replicas * vCPU * target_utilization / CPU_seconds_per_request
     */
    public static double apiCpuLimitRps(CapacityPlan plan) {
        Objects.requireNonNull(plan, "plan must not be null");
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
        Objects.requireNonNull(plan, "plan must not be null");
        if (plan.dependencyTrafficFraction() == 0) {
            return Double.POSITIVE_INFINITY;
        }
        return plan.dependencyPoolSize()
                / (plan.dependencyTrafficFraction() * plan.dependencyLatencySeconds());
    }

    /**
     * DB write limit:
     * RPS_db_write = DB_write_QPS_limit / (write_ratio * write_queries_per_request)
     */
    public static double dbWriteLimitRps(CapacityPlan plan) {
        Objects.requireNonNull(plan, "plan must not be null");
        if (plan.writeRatio() == 0 || plan.writeQueriesPerRequest() == 0) {
            return Double.POSITIVE_INFINITY;
        }
        return plan.dbWriteQpsLimit()
                / (plan.writeRatio() * plan.writeQueriesPerRequest());
    }
}
