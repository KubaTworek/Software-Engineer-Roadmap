package pl.jakubtworek.backend_engineering.stage_3.block_a.system_design.src.main.java.pl.jakubtworek.concepts;

/**
 * Describes an autoscaling policy independent of a specific cloud provider.
 *
 * The main design rule is simple:
 * - min replicas protect latency and reduce cold start impact
 * - max replicas protect backing services from overload
 * - scaling metric must match the real bottleneck
 */
public record AutoscalingConfig(
        int minReplicas,
        int maxReplicas,
        ScalingMetric scalingMetric,
        double targetValue
) {
    public AutoscalingConfig {
        if (minReplicas < 0) {
            throw new IllegalArgumentException("minReplicas must be non-negative");
        }
        if (maxReplicas < minReplicas) {
            throw new IllegalArgumentException("maxReplicas must be greater than or equal to minReplicas");
        }
        if (scalingMetric == null) {
            throw new IllegalArgumentException("scalingMetric is required");
        }
        if (targetValue <= 0) {
            throw new IllegalArgumentException("targetValue must be positive");
        }
    }

    /**
     * Returns true when the policy is likely suitable for IO-bound workloads.
     *
     * CPU alone is usually a weak scaling signal for IO-bound APIs.
     * Better signals include in-flight requests, queue depth, pool wait time,
     * dependency latency, or explicit concurrency.
     */
    public boolean isSuitableForIoBoundWorkload() {
        return scalingMetric != ScalingMetric.CPU_UTILIZATION;
    }
}
