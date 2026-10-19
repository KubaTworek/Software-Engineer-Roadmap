package pl.jakubtworek.backend_engineering.stage_3.block_a.implementation.tests;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/** Hard safety limits. They stop the experiment rather than merely failing its SLO. */
public record ExperimentGuardrails(
        Duration abortP99,
        double abortErrorRate,
        double abortSaturation,
        long abortQueueDepth) {

    public ExperimentGuardrails {
        if (abortP99 == null || abortP99.isNegative() || abortP99.isZero()) {
            throw new IllegalArgumentException("abortP99 must be positive");
        }
        if (!Double.isFinite(abortErrorRate) || abortErrorRate <= 0 || abortErrorRate > 1) {
            throw new IllegalArgumentException("abortErrorRate must be in range (0, 1]");
        }
        if (!Double.isFinite(abortSaturation) || abortSaturation <= 0 || abortSaturation > 1) {
            throw new IllegalArgumentException("abortSaturation must be in range (0, 1]");
        }
        if (abortQueueDepth < 1) throw new IllegalArgumentException("abortQueueDepth must be positive");
    }

    public List<String> violations(ExperimentObservation observation) {
        List<String> violations = new ArrayList<>();
        if (observation.p99().compareTo(abortP99) >= 0) violations.add("p99 latency reached abort threshold");
        if (observation.errorRate() >= abortErrorRate) violations.add("error rate reached abort threshold");
        if (Math.max(observation.applicationSaturation(), observation.dependencySaturation()) >= abortSaturation) {
            violations.add("resource saturation reached abort threshold");
        }
        if (observation.queueDepth() >= abortQueueDepth) violations.add("queue depth reached abort threshold");
        return List.copyOf(violations);
    }
}
