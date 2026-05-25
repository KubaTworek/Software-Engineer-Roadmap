package pl.jakubtworek.backend_engineering.stage_3.block_b.alerts;

import java.util.List;
import java.util.Objects;

/**
 * Represents a Prometheus rule group.
 *
 * Rule groups should be organized by service or ownership boundary.
 */
public final class PrometheusRuleGroup {

    private final String name;
    private final List<PrometheusAlertRule> rules;

    public PrometheusRuleGroup(String name, List<PrometheusAlertRule> rules) {
        this.name = requireNonBlank(name, "name");
        this.rules = List.copyOf(Objects.requireNonNull(rules, "rules must not be null"));

        if (this.rules.isEmpty()) {
            throw new IllegalArgumentException("rule group must contain at least one rule");
        }
    }

    public String name() {
        return name;
    }

    public List<PrometheusAlertRule> rules() {
        return rules;
    }

    private static String requireNonBlank(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return value;
    }
}