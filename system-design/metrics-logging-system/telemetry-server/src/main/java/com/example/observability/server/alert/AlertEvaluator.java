package com.example.observability.server.alert;

import com.example.observability.server.repository.TelemetryRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;

@Component
public class AlertEvaluator {
    private static final Logger log = LoggerFactory.getLogger(AlertEvaluator.class);
    private final AlertRuleStore ruleStore;
    private final TelemetryRepository repository;

    public AlertEvaluator(AlertRuleStore ruleStore, TelemetryRepository repository) {
        this.ruleStore = ruleStore;
        this.repository = repository;
    }

    @Scheduled(fixedDelayString = "${telemetry.alerting.evaluation-interval-ms}")
    public void evaluateRules() {
        for (AlertRule rule : ruleStore.all()) {
            if (!rule.isEnabled()) continue;
            try {
                Instant end = Instant.now();
                Instant start = end.minusSeconds(rule.getWindowSeconds());
                double value = repository.evaluateMetric(rule, start, end);
                boolean firing = compare(value, rule.getOperator(), rule.getThreshold());
                if (firing) {
                    AlertEvent event = new AlertEvent(
                            rule.getTenantId(), rule.getId(), rule.getName(), "FIRING", end,
                            value, rule.getThreshold(), "Rule " + rule.getName() + " is firing: observed=" + value
                    );
                    repository.insertAlertEvent(event);
                    log.warn("ALERT FIRING: {} observed={} threshold={}", rule.getName(), value, rule.getThreshold());
                }
            } catch (Exception e) {
                log.error("Alert evaluation failed for rule {}", rule.getName(), e);
            }
        }
    }

    private boolean compare(double value, String operator, double threshold) {
        return switch (operator) {
            case ">" -> value > threshold;
            case ">=" -> value >= threshold;
            case "<" -> value < threshold;
            case "<=" -> value <= threshold;
            case "==" -> Double.compare(value, threshold) == 0;
            case "!=" -> Double.compare(value, threshold) != 0;
            default -> value > threshold;
        };
    }
}
