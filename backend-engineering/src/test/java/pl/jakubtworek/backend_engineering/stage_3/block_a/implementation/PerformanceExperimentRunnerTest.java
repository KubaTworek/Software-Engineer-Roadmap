package pl.jakubtworek.backend_engineering.stage_3.block_a.implementation;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import pl.jakubtworek.backend_engineering.stage_3.block_a.concepts.capacity.CapacityPlan;
import pl.jakubtworek.backend_engineering.stage_3.block_a.implementation.tests.CapacityHypothesis;
import pl.jakubtworek.backend_engineering.stage_3.block_a.implementation.tests.ExperimentGuardrails;
import pl.jakubtworek.backend_engineering.stage_3.block_a.implementation.tests.ExperimentObservation;
import pl.jakubtworek.backend_engineering.stage_3.block_a.implementation.tests.LoadTestType;
import pl.jakubtworek.backend_engineering.stage_3.block_a.implementation.tests.PerformanceExperimentPlan;
import pl.jakubtworek.backend_engineering.stage_3.block_a.implementation.tests.PerformanceExperimentRunner;
import pl.jakubtworek.backend_engineering.stage_3.block_a.implementation.tests.PerformanceObjectives;
import pl.jakubtworek.backend_engineering.stage_3.block_a.implementation.tests.WorkloadPhase;
import pl.jakubtworek.backend_engineering.stage_3.block_a.implementation.tests.WorkloadProfile;

class PerformanceExperimentRunnerTest {

    @Test
    void guardrailMustStopExperimentBeforeNextPhase() throws Exception {
        List<String> executed = new ArrayList<>();
        PerformanceExperimentRunner runner = new PerformanceExperimentRunner(phase -> {
            executed.add(phase.name());
            if (phase.name().equals("stress")) {
                return observation(phase.name(), 400, Duration.ofSeconds(3), 0.25, 0.99, 20_000);
            }
            return observation(phase.name(), 200, Duration.ofMillis(200), 0, 0.60, 0);
        });

        PerformanceExperimentRunner.ExperimentReport report = runner.run(plan());

        assertThat(executed).containsExactly("load", "stress");
        assertThat(report.aborted()).isTrue();
        assertThat(report.abortedAfterPhase()).isEqualTo("stress");
        assertThat(report.abortReasons()).contains(
                "p99 latency reached abort threshold",
                "error rate reached abort threshold",
                "resource saturation reached abort threshold",
                "queue depth reached abort threshold");
    }

    private static PerformanceExperimentPlan plan() {
        CapacityPlan capacity = new CapacityPlan(2, 2, 0.7, 0.01, 50, 1, 0.1, 1_000, 0.5, 2);
        return new PerformanceExperimentPlan(
                "checkout-capacity",
                CapacityHypothesis.from(capacity, 0.2),
                new PerformanceObjectives(Duration.ofMillis(200), Duration.ofMillis(400), 0.01, 0.80, 0.95),
                new ExperimentGuardrails(Duration.ofSeconds(2), 0.20, 0.98, 10_000),
                List.of(
                        new WorkloadPhase("load", LoadTestType.LOAD, WorkloadProfile.openAtRps(200), Duration.ofMinutes(5)),
                        new WorkloadPhase("stress", LoadTestType.STRESS, WorkloadProfile.openAtRps(400), Duration.ofMinutes(5)),
                        new WorkloadPhase("soak", LoadTestType.SOAK, WorkloadProfile.openAtRps(200), Duration.ofHours(2))));
    }

    private static ExperimentObservation observation(
            String phase, int rps, Duration p99, double errorRate, double saturation, long queueDepth) {
        return new ExperimentObservation(
                phase, rps, rps, p99.dividedBy(2), p99,
                errorRate, saturation, 0.50, queueDepth, 0, 2);
    }
}
