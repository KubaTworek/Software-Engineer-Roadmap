package pl.jakubtworek.backend_engineering.stage_3.block_a.implementation.tests;

import java.util.ArrayList;
import java.util.List;

/** Evaluates SLO, controlled degradation and autoscaling from observation windows. */
public final class PerformanceExperimentEvaluator {

    public PointAssessment assess(ExperimentObservation observation, PerformanceObjectives objectives) {
        List<String> failures = new ArrayList<>();
        if (observation.p95().compareTo(objectives.maxP95()) > 0) failures.add("p95 exceeded SLO");
        if (observation.p99().compareTo(objectives.maxP99()) > 0) failures.add("p99 exceeded SLO");
        if (observation.errorRate() > objectives.maxErrorRate()) failures.add("error rate exceeded SLO");
        if (observation.applicationSaturation() > objectives.maxSaturation()) failures.add("application saturated");
        if (observation.dependencySaturation() > objectives.maxSaturation()) failures.add("dependency saturated");
        if (observation.throughputRatio() < objectives.minThroughputRatio()) failures.add("throughput did not follow offered load");
        return new PointAssessment(failures.isEmpty(), List.copyOf(failures));
    }

    public boolean controlledDegradation(
            ExperimentObservation observation,
            CapacityHypothesis hypothesis,
            ExperimentGuardrails guardrails) {
        return observation.offeredRps() > hypothesis.firstBottleneck().limitRps()
                && observation.loadShedRate() > 0
                && observation.errorRate() >= observation.loadShedRate()
                && guardrails.violations(observation).isEmpty();
    }

    public AutoscalingAssessment assessAutoscaling(
            ExperimentObservation baseline,
            ExperimentObservation pressure,
            ExperimentObservation recovered,
            PerformanceObjectives objectives) {
        if (pressure.replicas() <= baseline.replicas()) {
            return new AutoscalingAssessment(AutoscalingStatus.NOT_TRIGGERED, "replica count did not increase");
        }
        if (pressure.dependencySaturation() > objectives.maxSaturation()) {
            return new AutoscalingAssessment(
                    AutoscalingStatus.UNSAFE,
                    "scaling the application increased pressure on an already saturated dependency");
        }
        PointAssessment recovery = assess(recovered, objectives);
        if (recovered.replicas() >= pressure.replicas() && recovery.passed()) {
            return new AutoscalingAssessment(AutoscalingStatus.EFFECTIVE, "capacity recovered after replicas became ready");
        }
        return new AutoscalingAssessment(AutoscalingStatus.INEFFECTIVE, "replicas increased but user-visible SLO did not recover");
    }

    public record PointAssessment(boolean passed, List<String> failures) {
    }

    public record AutoscalingAssessment(AutoscalingStatus status, String reason) {
    }

    public enum AutoscalingStatus {
        EFFECTIVE,
        NOT_TRIGGERED,
        INEFFECTIVE,
        UNSAFE
    }
}
