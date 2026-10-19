package pl.jakubtworek.backend_engineering.stage_2.block_c.progressive_delivery;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/** Safety contract required before injecting a fault outside an isolated test. */
public record GameDayPlan(String hypothesis, String target, int trafficPercent,
                          Duration maximumDuration, String abortCondition,
                          String recoveryAction, boolean approved) {

    public List<String> validate(int allowedTrafficPercent, Duration allowedDuration) {
        List<String> violations = new ArrayList<>();
        if (hypothesis == null || hypothesis.isBlank()) violations.add("hypothesis is required");
        if (target == null || target.isBlank()) violations.add("target is required");
        if (trafficPercent < 1 || trafficPercent > allowedTrafficPercent) violations.add("blast radius exceeds limit");
        if (maximumDuration == null || maximumDuration.isZero() || maximumDuration.isNegative()
                || maximumDuration.compareTo(allowedDuration) > 0) {
            violations.add("duration exceeds limit");
        }
        if (abortCondition == null || abortCondition.isBlank()) violations.add("abort condition is required");
        if (recoveryAction == null || recoveryAction.isBlank()) violations.add("recovery action is required");
        if (!approved) violations.add("explicit approval is required");
        return List.copyOf(violations);
    }
}
