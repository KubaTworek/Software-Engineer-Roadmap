package pl.jakubtworek.backend_engineering.stage_3.block_a.metrics.src.main.java.com.example.systemdesign.metrics;

/**
 * Represents a high-level area of the system that should be monitored.
 *
 * These categories map to the most common operational surfaces:
 * API, database, cache, rate limiting, resilience mechanisms, and queues.
 */
public enum MetricCategory {
    API,
    DATABASE,
    REDIS,
    RATE_LIMITING,
    RESILIENCE,
    QUEUE
}
