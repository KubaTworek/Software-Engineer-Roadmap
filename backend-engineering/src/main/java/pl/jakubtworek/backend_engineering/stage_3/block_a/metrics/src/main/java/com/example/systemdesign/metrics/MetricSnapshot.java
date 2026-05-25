package pl.jakubtworek.backend_engineering.stage_3.block_a.metrics.src.main.java.com.example.systemdesign.metrics;

import java.util.Map;

/**
 * Snapshot of metrics collected from one system component.
 *
 * This class is intentionally simple.
 * In production, values would usually come from Prometheus, CloudWatch,
 * OpenTelemetry, Datadog, Grafana Mimir, or another metrics backend.
 */
public class MetricSnapshot {

    private final Map<String, Double> values;

    public MetricSnapshot(Map<String, Double> values) {
        if (values == null) {
            throw new IllegalArgumentException("values are required");
        }

        this.values = Map.copyOf(values);
    }

    public double get(String metricName) {
        Double value = values.get(metricName);

        if (value == null) {
            throw new IllegalArgumentException("Missing metric: " + metricName);
        }

        return value;
    }

    public boolean has(String metricName) {
        return values.containsKey(metricName);
    }
}