package pl.jakubtworek.backend_engineering.stage_3.block_a.implementation;

import org.junit.jupiter.api.Test;
import pl.jakubtworek.backend_engineering.stage_3.block_a.implementation.tests.LoadTestRunner;
import pl.jakubtworek.backend_engineering.stage_3.block_a.implementation.tests.LoadTestScenario;
import pl.jakubtworek.backend_engineering.stage_3.block_a.implementation.tests.LoadTestType;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LoadTestRunnerTest {

    @Test
    void stepScenarioReachesTargetAndPreservesTotalDuration() throws Exception {
        List<TrafficStep> calls = new ArrayList<>();
        LoadTestRunner runner = new LoadTestRunner((rps, seconds) -> calls.add(new TrafficStep(rps, seconds)));
        LoadTestScenario scenario = new LoadTestScenario(
                LoadTestType.STEP,
                Duration.ofSeconds(12),
                100,
                350,
                "Find the latency knee"
        );

        runner.run(scenario);

        assertThat(calls).hasSize(5);
        assertThat(calls.get(0).rps()).isEqualTo(100);
        assertThat(calls.get(calls.size() - 1).rps()).isEqualTo(350);
        assertThat(calls).extracting(TrafficStep::seconds).containsExactly(3L, 3L, 2L, 2L, 2L);
        assertThat(calls.stream().mapToLong(TrafficStep::seconds).sum()).isEqualTo(12);
    }

    @Test
    void rejectsScenariosThatCannotBeRepresentedInWholeSeconds() {
        assertThatThrownBy(() -> new LoadTestScenario(
                LoadTestType.BASELINE,
                Duration.ofMillis(500),
                0,
                10,
                "Too short"
        )).isInstanceOf(IllegalArgumentException.class);

        assertThatThrownBy(() -> new LoadTestScenario(
                LoadTestType.SPIKE,
                Duration.ofSeconds(1),
                10,
                100,
                "No time for both phases"
        )).isInstanceOf(IllegalArgumentException.class);
    }

    private record TrafficStep(int rps, long seconds) {
    }
}
