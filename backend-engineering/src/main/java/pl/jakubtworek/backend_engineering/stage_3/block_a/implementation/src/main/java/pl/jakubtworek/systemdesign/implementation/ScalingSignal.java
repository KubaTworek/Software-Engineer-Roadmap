package pl.jakubtworek.backend_engineering.stage_3.block_a.implementation.src.main.java.pl.jakubtworek.systemdesign.implementation;

/**
 * Signals that can be used by autoscaling policies.
 *
 * CPU is often useful for CPU-bound workloads.
 * IO-bound workloads usually need signals such as concurrency, queue depth,
 * pool wait, or dependency latency.
 */
public enum ScalingSignal {
    CPU_UTILIZATION,
    MEMORY_UTILIZATION,
    REQUEST_CONCURRENCY,
    IN_FLIGHT_REQUESTS,
    QUEUE_DEPTH,
    DATABASE_POOL_WAIT,
    DEPENDENCY_P95_LATENCY,
    CUSTOM_BUSINESS_METRIC
}
