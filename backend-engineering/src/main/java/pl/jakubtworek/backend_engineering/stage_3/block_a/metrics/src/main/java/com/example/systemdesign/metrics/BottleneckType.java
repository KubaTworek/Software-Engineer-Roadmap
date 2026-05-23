package pl.jakubtworek.backend_engineering.stage_3.block_a.metrics.src.main.java.com.example.systemdesign.metrics;

/**
 * Represents a predicted first limiting component on the request path.
 */
public enum BottleneckType {
    API_CPU,
    DEPENDENCY_POOL,
    DATABASE_WRITE
}
