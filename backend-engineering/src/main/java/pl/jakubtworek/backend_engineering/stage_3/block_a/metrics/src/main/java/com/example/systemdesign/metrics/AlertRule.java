package pl.jakubtworek.backend_engineering.stage_3.block_a.metrics.src.main.java.com.example.systemdesign.metrics;

/**
 * One executable alert rule.
 *
 * The rule evaluates a MetricSnapshot and returns an AlertResult.
 */
public class AlertRule {

    private final String name;
    private final AlertSeverity severity;
    private final AlertCondition condition;
    private final String explanationWhenFiring;
    private final String explanationWhenHealthy;

    public AlertRule(
            String name,
            AlertSeverity severity,
            AlertCondition condition,
            String explanationWhenFiring,
            String explanationWhenHealthy
    ) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("name is required");
        }

        if (severity == null) {
            throw new IllegalArgumentException("severity is required");
        }

        if (condition == null) {
            throw new IllegalArgumentException("condition is required");
        }

        this.name = name;
        this.severity = severity;
        this.condition = condition;
        this.explanationWhenFiring = explanationWhenFiring;
        this.explanationWhenHealthy = explanationWhenHealthy;
    }

    public AlertResult evaluate(MetricSnapshot snapshot) {
        boolean firing = condition.matches(snapshot);

        return new AlertResult(
                name,
                severity,
                firing,
                firing ? explanationWhenFiring : explanationWhenHealthy
        );
    }
}