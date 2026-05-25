package pl.jakubtworek.backend_engineering.stage_3.block_b.alerts;

import java.util.Objects;

/**
 * Represents a single Prometheus alerting rule.
 *
 * The expression should alert on user-facing symptoms whenever possible.
 * Cause-based alerts should usually be warnings or tickets unless they directly affect users.
 */
public final class PrometheusAlertRule {

    private final String alertName;
    private final String expression;
    private final String forDuration;
    private final String keepFiringFor;
    private final AlertLabels labels;
    private final AlertAnnotations annotations;

    public PrometheusAlertRule(
            String alertName,
            String expression,
            String forDuration,
            String keepFiringFor,
            AlertLabels labels,
            AlertAnnotations annotations
    ) {
        this.alertName = requireNonBlank(alertName, "alertName");
        this.expression = requireNonBlank(expression, "expression");
        this.forDuration = requireNonBlank(forDuration, "forDuration");
        this.keepFiringFor = keepFiringFor;
        this.labels = Objects.requireNonNull(labels, "labels must not be null");
        this.annotations = Objects.requireNonNull(annotations, "annotations must not be null");
    }

    public String alertName() {
        return alertName;
    }

    public String expression() {
        return expression;
    }

    public String forDuration() {
        return forDuration;
    }

    public String keepFiringFor() {
        return keepFiringFor;
    }

    public AlertLabels labels() {
        return labels;
    }

    public AlertAnnotations annotations() {
        return annotations;
    }

    private static String requireNonBlank(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return value;
    }
}