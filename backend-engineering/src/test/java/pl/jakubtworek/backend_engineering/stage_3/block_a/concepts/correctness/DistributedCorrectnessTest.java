package pl.jakubtworek.backend_engineering.stage_3.block_a.concepts.correctness;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

class DistributedCorrectnessTest {

    private static final Instant START = Instant.parse("2026-01-01T00:00:00Z");

    @Test
    void successfulRequestsDoNotProveThatTheBusinessInvariantWasPreserved() {
        List<IncrementCommand> commands = commands(3);
        FailurePlan failures = new FailurePlan(Set.of("command-0", "command-2"));

        DeterministicRetrySimulation.SimulationReport report =
                runScenario(new NaiveCounterStore(), commands, failures, 41L, 20);

        assertThat(report.allRequestsSucceeded()).isTrue();
        assertThat(report.livenessPreserved()).isTrue();
        assertThat(report.safetyPreserved()).isFalse();
        assertThat(report.expectedValue()).isEqualTo(3);
        assertThat(report.actualValue()).isEqualTo(5);
        assertThat(report.history())
                .filteredOn(event -> event.type() == HistoryEvent.Type.TIMED_OUT)
                .hasSize(2);
    }

    @Test
    void idempotencyPreservesTheInvariantAcrossTheSameTimeoutsAndRetries() {
        List<IncrementCommand> commands = commands(3);
        FailurePlan failures = new FailurePlan(Set.of("command-0", "command-2"));

        DeterministicRetrySimulation.SimulationReport report =
                runScenario(new IdempotentCounterStore(), commands, failures, 41L, 20);

        assertThat(report.allRequestsSucceeded()).isTrue();
        assertThat(report.livenessPreserved()).isTrue();
        assertThat(report.safetyPreserved()).isTrue();
        assertThat(report.actualValue()).isEqualTo(3);
        assertThat(report.history())
                .filteredOn(event -> event.type() == HistoryEvent.Type.DUPLICATE_SUPPRESSED)
                .extracting(HistoryEvent::commandId)
                .containsExactlyInAnyOrder("command-0", "command-2");
    }

    @Test
    void aSeedReplaysRandomFailuresAndInterleavingsExactly() {
        List<IncrementCommand> commands = commands(20);
        FailurePlan firstPlan = FailurePlan.random(commands, 991L, 0.45);
        FailurePlan secondPlan = FailurePlan.random(commands, 991L, 0.45);

        DeterministicRetrySimulation.SimulationReport first =
                runScenario(new IdempotentCounterStore(), commands, firstPlan, 123L, 100);
        DeterministicRetrySimulation.SimulationReport replay =
                runScenario(new IdempotentCounterStore(), commands, secondPlan, 123L, 100);

        assertThat(firstPlan.timeoutAfterFirstEffect()).isNotEmpty();
        assertThat(secondPlan).isEqualTo(firstPlan);
        assertThat(replay.history()).isEqualTo(first.history());
        assertThat(replay.schedulerTrace()).isEqualTo(first.schedulerTrace());
        assertThat(replay.safetyPreserved()).isTrue();
    }

    @Test
    void safetyCanHoldWhileLivenessFailsWhenTheStepBudgetIsExhausted() {
        List<IncrementCommand> commands = List.of(new IncrementCommand("command-0", 1));
        FailurePlan failures = new FailurePlan(Set.of("command-0"));

        DeterministicRetrySimulation.SimulationReport report =
                runScenario(new IdempotentCounterStore(), commands, failures, 7L, 1);

        assertThat(report.safetyPreserved()).isTrue();
        assertThat(report.livenessPreserved()).isFalse();
        assertThat(report.allRequestsSucceeded()).isFalse();
        assertThat(report.actualValue()).isEqualTo(1);
    }

    private static DeterministicRetrySimulation.SimulationReport runScenario(
            CounterStore store,
            List<IncrementCommand> commands,
            FailurePlan failurePlan,
            long schedulerSeed,
            int taskBudget) {
        ControlledClock clock = new ControlledClock(START);
        DeterministicScheduler scheduler = new DeterministicScheduler(clock, schedulerSeed);
        return new DeterministicRetrySimulation(store, clock, scheduler, failurePlan)
                .run(commands, taskBudget);
    }

    private static List<IncrementCommand> commands(int count) {
        return IntStream.range(0, count)
                .mapToObj(index -> new IncrementCommand("command-" + index, 1))
                .toList();
    }
}
