package pl.jakubtworek.backend_engineering.stage_3.block_a.implementation.src.main.java.pl.jakubtworek.systemdesign.implementation;

/**
 * Defines guardrails for autoscaling.
 *
 * Minimum replicas protect latency and reduce cold start impact.
 * Maximum replicas protect backing services from accidental overload.
 */
public record AutoscalingGuardrails(
        int minReplicas,
        int maxReplicas,
        ScalingSignal scalingSignal,
        WorkloadType workloadType,
        int maxDatabaseConnectionsPerReplica,
        int databaseConnectionLimit
) {
    public AutoscalingGuardrails {
        if (minReplicas < 0) {
            throw new IllegalArgumentException("minReplicas must be non-negative");
        }
        if (maxReplicas < minReplicas) {
            throw new IllegalArgumentException("maxReplicas must be greater than or equal to minReplicas");
        }
        if (scalingSignal == null) {
            throw new IllegalArgumentException("scalingSignal is required");
        }
        if (workloadType == null) {
            throw new IllegalArgumentException("workloadType is required");
        }
        if (maxDatabaseConnectionsPerReplica < 0) {
            throw new IllegalArgumentException("maxDatabaseConnectionsPerReplica must be non-negative");
        }
        if (databaseConnectionLimit < 0) {
            throw new IllegalArgumentException("databaseConnectionLimit must be non-negative");
        }
    }

    /**
     * Detects a common anti-pattern:
     * scaling an IO-bound workload only by CPU utilization.
     */
    public boolean usesWeakSignalForIoBoundWorkload() {
        return workloadType == WorkloadType.IO_BOUND && scalingSignal == ScalingSignal.CPU_UTILIZATION;
    }

    /**
     * Estimates whether the configured max replicas can exceed the database connection limit.
     */
    public boolean canOverloadDatabaseConnections() {
        return maxDatabaseConnectionsPerReplica > 0
                && databaseConnectionLimit > 0
                && (maxReplicas * maxDatabaseConnectionsPerReplica) > databaseConnectionLimit;
    }
}
