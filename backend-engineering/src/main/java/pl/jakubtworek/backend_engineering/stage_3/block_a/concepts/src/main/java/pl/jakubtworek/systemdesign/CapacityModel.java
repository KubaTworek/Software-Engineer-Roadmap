package pl.jakubtworek.backend_engineering.stage_3.block_a.concepts.src.main.java.pl.jakubtworek.systemdesign;

/**
 * Represents basic capacity formulas used during system design.
 *
 * The goal of this class is not to replace load testing.
 * It gives a first-order model that helps predict which component
 * will become the bottleneck before the system is tested under load.
 */
public final class CapacityModel {

    private CapacityModel() {
        // Utility class.
    }

    /**
     * Little's Law:
     *
     * concurrency = throughput * latency
     *
     * Throughput is expressed as requests per second.
     * Latency is expressed in seconds.
     */
    public static double concurrency(double throughputRps, double latencySeconds) {
        requireNonNegative(throughputRps, "throughputRps");
        requireNonNegative(latencySeconds, "latencySeconds");
        return throughputRps * latencySeconds;
    }

    /**
     * Estimates the maximum API throughput limited by CPU.
     *
     * Example:
     * - 8 CPU cores
     * - target CPU utilization: 0.7
     * - average CPU time per request: 20 ms
     *
     * max RPS = (8 * 0.7) / 0.020 = 280 RPS
     */
    public static double cpuLimitedThroughput(
            double cpuCores,
            double targetUtilization,
            double cpuSecondsPerRequest
    ) {
        requirePositive(cpuCores, "cpuCores");
        requirePositive(cpuSecondsPerRequest, "cpuSecondsPerRequest");

        if (targetUtilization <= 0 || targetUtilization > 1) {
            throw new IllegalArgumentException("targetUtilization must be in range (0, 1]");
        }

        return (cpuCores * targetUtilization) / cpuSecondsPerRequest;
    }

    /**
     * Estimates throughput limited by a bounded dependency pool.
     *
     * Example:
     * - database connection pool: 50 connections
     * - average dependency latency: 100 ms
     *
     * max RPS = 50 / 0.100 = 500 RPS
     */
    public static double poolLimitedThroughput(int poolSize, double dependencyLatencySeconds) {
        if (poolSize <= 0) {
            throw new IllegalArgumentException("poolSize must be positive");
        }

        requirePositive(dependencyLatencySeconds, "dependencyLatencySeconds");
        return poolSize / dependencyLatencySeconds;
    }

    /**
     * Estimates how many replicas are required to serve a given load.
     *
     * This is a planning value. In production, it should be validated
     * with step tests and adjusted using p95/p99 latency behavior.
     */
    public static int requiredReplicas(double requiredRps, double safeRpsPerReplica) {
        requirePositive(requiredRps, "requiredRps");
        requirePositive(safeRpsPerReplica, "safeRpsPerReplica");

        return (int) Math.ceil(requiredRps / safeRpsPerReplica);
    }

    private static void requirePositive(double value, String name) {
        if (value <= 0) {
            throw new IllegalArgumentException(name + " must be positive");
        }
    }

    private static void requireNonNegative(double value, String name) {
        if (value < 0) {
            throw new IllegalArgumentException(name + " must be non-negative");
        }
    }
}
