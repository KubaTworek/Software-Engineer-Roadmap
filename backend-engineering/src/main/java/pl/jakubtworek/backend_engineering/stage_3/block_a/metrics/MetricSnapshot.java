package pl.jakubtworek.backend_engineering.stage_3.block_a.metrics;

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

        values.forEach((name, value) -> {
            if (name == null || name.isBlank()) throw new IllegalArgumentException("metric name is required");
            if (value == null || !Double.isFinite(value)) throw new IllegalArgumentException("metric value must be finite: " + name);
        });

        this.values = Map.copyOf(values);
    }

    public double get(String metricName) {
        if (metricName == null || metricName.isBlank()) throw new IllegalArgumentException("metricName is required");
        Double value = values.get(metricName);

        if (value == null) {
            throw new IllegalArgumentException("Missing metric: " + metricName);
        }

        return value;
    }

    public boolean has(String metricName) {
        if (metricName == null || metricName.isBlank()) throw new IllegalArgumentException("metricName is required");
        return values.containsKey(metricName);
    }
}
