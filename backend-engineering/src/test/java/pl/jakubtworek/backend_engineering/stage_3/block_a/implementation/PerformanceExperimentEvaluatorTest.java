package pl.jakubtworek.backend_engineering.stage_3.block_a.implementation;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import org.junit.jupiter.api.Test;
import pl.jakubtworek.backend_engineering.stage_3.block_a.concepts.capacity.CapacityPlan;
import pl.jakubtworek.backend_engineering.stage_3.block_a.implementation.tests.CapacityHypothesis;
import pl.jakubtworek.backend_engineering.stage_3.block_a.implementation.tests.ExperimentGuardrails;
import pl.jakubtworek.backend_engineering.stage_3.block_a.implementation.tests.ExperimentObservation;
import pl.jakubtworek.backend_engineering.stage_3.block_a.implementation.tests.PerformanceExperimentEvaluator;
import pl.jakubtworek.backend_engineering.stage_3.block_a.implementation.tests.PerformanceObjectives;

class PerformanceExperimentEvaluatorTest {

    private final PerformanceObjectives objectives = new PerformanceObjectives(
            Duration.ofMillis(200), Duration.ofMillis(400), 0.01, 0.80, 0.95);
    private final ExperimentGuardrails guardrails = new ExperimentGuardrails(
            Duration.ofSeconds(2), 0.20, 0.98, 10_000);
    private final PerformanceExperimentEvaluator evaluator = new PerformanceExperimentEvaluator();

    @Test
    void pointAssessmentMustUseTailLatencyThroughputAndSaturationNotAverageLatency() {
        ExperimentObservation observation = observation("load", 200, 199, 180, 450, 0.005, 0.70, 0.60, 0, 0, 2);

        PerformanceExperimentEvaluator.PointAssessment result = evaluator.assess(observation, objectives);

        assertThat(result.passed()).isFalse();
        assertThat(result.failures()).containsExactly("p99 exceeded SLO");
    }

    @Test
    void overloadIsControlledOnlyWhenLoadIsShedBeforeHardAbortThreshold() {
        CapacityHypothesis hypothesis = CapacityHypothesis.from(capacityPlan(), 0.2);
        ExperimentObservation overload = observation(
                "stress", 350, 280, 300, 900, 0.19, 0.90, 0.70, 200, 0.18, 2);

        assertThat(evaluator.controlledDegradation(overload, hypothesis, guardrails)).isTrue();
    }

    @Test
    void autoscalingMustRecoverUserSloWithoutSaturatingDependency() {
        ExperimentObservation baseline = observation("baseline", 150, 150, 100, 180, 0, 0.50, 0.40, 0, 0, 2);
        ExperimentObservation pressure = observation("spike", 300, 250, 300, 600, 0.02, 0.85, 0.65, 100, 0.01, 4);
        ExperimentObservation recovered = observation("recovered", 300, 295, 150, 250, 0.005, 0.65, 0.70, 10, 0, 4);

        PerformanceExperimentEvaluator.AutoscalingAssessment result =
                evaluator.assessAutoscaling(baseline, pressure, recovered, objectives);

        assertThat(result.status()).isEqualTo(PerformanceExperimentEvaluator.AutoscalingStatus.EFFECTIVE);
    }

    @Test
    void addingApplicationReplicasIsUnsafeWhenDependencyIsAlreadySaturated() {
        ExperimentObservation baseline = observation("baseline", 150, 150, 100, 180, 0, 0.50, 0.60, 0, 0, 2);
        ExperimentObservation pressure = observation("spike", 300, 220, 350, 700, 0.03, 0.75, 0.95, 500, 0, 4);
        ExperimentObservation recovered = observation("recovered", 300, 230, 320, 650, 0.02, 0.70, 0.97, 700, 0, 4);

        PerformanceExperimentEvaluator.AutoscalingAssessment result =
                evaluator.assessAutoscaling(baseline, pressure, recovered, objectives);

        assertThat(result.status()).isEqualTo(PerformanceExperimentEvaluator.AutoscalingStatus.UNSAFE);
    }

    private static CapacityPlan capacityPlan() {
        return new CapacityPlan(2, 2, 0.7, 0.01, 50, 1, 0.1, 1_000, 0.5, 2);
    }

    private static ExperimentObservation observation(
            String phase,
            int offeredRps,
            double achievedRps,
            long p95Millis,
            long p99Millis,
            double errorRate,
            double appSaturation,
            double dependencySaturation,
            long queueDepth,
            double loadShedRate,
            int replicas) {
        return new ExperimentObservation(
                phase, offeredRps, achievedRps,
                Duration.ofMillis(p95Millis), Duration.ofMillis(p99Millis),
                errorRate, appSaturation, dependencySaturation, queueDepth, loadShedRate, replicas);
    }
}
