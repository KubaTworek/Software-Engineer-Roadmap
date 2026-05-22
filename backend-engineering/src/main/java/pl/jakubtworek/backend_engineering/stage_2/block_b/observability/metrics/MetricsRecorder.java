package pl.jakubtworek.backend_engineering.stage_2.block_b.observability.metrics;

import java.util.Map;

/**
 * Abstraction for recording application and infrastructure metrics.
 *
 * A production implementation may delegate to Micrometer, Prometheus,
 * OpenTelemetry Metrics or another metrics backend.
 */
public interface MetricsRecorder {

    /**
     * Increments a counter metric.
     */
    void incrementCounter(String name, Map<String, String> tags);

    /**
     * Records a gauge value.
     */
    void recordGauge(String name, double value, Map<String, String> tags);

    /**
     * Records a duration in milliseconds.
     */
    void recordDuration(String name, long durationMillis, Map<String, String> tags);
}