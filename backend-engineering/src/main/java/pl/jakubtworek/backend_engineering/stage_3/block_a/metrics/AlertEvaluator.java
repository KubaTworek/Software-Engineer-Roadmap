package pl.jakubtworek.backend_engineering.stage_3.block_a.metrics;

import java.util.List;

/**
 * Evaluates multiple alert rules against one metrics snapshot.
 */
public class AlertEvaluator {

    private final List<AlertRule> rules;

    public AlertEvaluator(List<AlertRule> rules) {
        if (rules == null || rules.isEmpty()) {
            throw new IllegalArgumentException("At least one alert rule is required");
        }

        this.rules = List.copyOf(rules);
    }

    public List<AlertResult> evaluate(MetricSnapshot snapshot) {
        if (snapshot == null) throw new IllegalArgumentException("snapshot is required");
        return rules.stream()
                .map(rule -> rule.evaluate(snapshot))
                .toList();
    }

    public List<AlertResult> firingAlerts(MetricSnapshot snapshot) {
        return evaluate(snapshot).stream()
                .filter(AlertResult::firing)
                .toList();
    }
}
