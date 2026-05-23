package pl.jakubtworek.backend_engineering.stage_3.block_a.system_design.src.main.java.pl.jakubtworek.concepts;

/**
 * Common metrics used for horizontal autoscaling.
 */
public enum ScalingMetric {
    CPU_UTILIZATION,
    MEMORY_UTILIZATION,
    REQUEST_CONCURRENCY,
    IN_FLIGHT_REQUESTS,
    QUEUE_DEPTH,
    DATABASE_POOL_WAIT,
    DEPENDENCY_P95_LATENCY
}
