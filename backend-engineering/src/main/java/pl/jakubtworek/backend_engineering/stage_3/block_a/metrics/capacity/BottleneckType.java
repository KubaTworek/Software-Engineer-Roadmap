package pl.jakubtworek.backend_engineering.stage_3.block_a.metrics.capacity;

/**
 * Type of predicted bottleneck.
 */
public enum BottleneckType {
    API_CPU,
    DEPENDENCY_POOL,
    DB_WRITE
}