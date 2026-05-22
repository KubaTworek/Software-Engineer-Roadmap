package pl.jakubtworek.backend_engineering.stage_2.block_b.observability.metrics;

import java.util.Map;

/**
 * Simple console implementation of metrics recording.
 *
 * This class is useful for examples, but production systems should export
 * metrics to a real monitoring backend.
 */
public class ConsoleMetricsRecorder implements MetricsRecorder {

    /**
     * Records a counter increment.
     */
    @Override
    public void incrementCounter(String name, Map<String, String> tags) {
        System.out.println("counter=" + name + ", tags=" + tags);
    }

    /**
     * Records a gauge value.
     */
    @Override
    public void recordGauge(String name, double value, Map<String, String> tags) {
        System.out.println("gauge=" + name + ", value=" + value + ", tags=" + tags);
    }

    /**
     * Records a duration value.
     */
    @Override
    public void recordDuration(String name, long durationMillis, Map<String, String> tags) {
        System.out.println("duration=" + name + ", valueMs=" + durationMillis + ", tags=" + tags);
    }
}