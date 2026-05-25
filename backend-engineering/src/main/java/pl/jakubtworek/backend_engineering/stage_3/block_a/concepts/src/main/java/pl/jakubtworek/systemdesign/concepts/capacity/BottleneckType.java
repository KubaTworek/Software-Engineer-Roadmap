package pl.jakubtworek.backend_engineering.stage_3.block_a.concepts.src.main.java.pl.jakubtworek.systemdesign.concepts.capacity;

/**
 * The component predicted to become the first bottleneck.
 */
public enum BottleneckType {
    API_CPU,
    DEPENDENCY_POOL,
    DB_WRITE
}