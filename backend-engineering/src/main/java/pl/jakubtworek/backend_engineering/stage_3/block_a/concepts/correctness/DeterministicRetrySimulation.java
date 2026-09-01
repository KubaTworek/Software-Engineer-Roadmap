package pl.jakubtworek.backend_engineering.stage_3.block_a.concepts.correctness;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Models an ambiguous timeout after commit. A retry uses the same command id, so
 * the store decides whether the logical effect is executed once or twice.
 */
public final class DeterministicRetrySimulation {

    private final CounterStore store;
    private final ControlledClock clock;
    private final DeterministicScheduler scheduler;
    private final FailurePlan failurePlan;
    private final List<HistoryEvent> history = new ArrayList<>();
    private final Set<String> completedCommands = new HashSet<>();
    private long historySequence;

    public DeterministicRetrySimulation(
            CounterStore store,
            ControlledClock clock,
            DeterministicScheduler scheduler,
            FailurePlan failurePlan) {
        this.store = Objects.requireNonNull(store, "store must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler must not be null");
        this.failurePlan = Objects.requireNonNull(failurePlan, "failurePlan must not be null");
    }

    public SimulationReport run(List<IncrementCommand> commands, int taskBudget) {
        List<IncrementCommand> scenario = List.copyOf(commands);
        rejectDuplicateLogicalIds(scenario);
        for (IncrementCommand command : scenario) {
            scheduler.schedule("invoke:" + command.commandId(), Duration.ZERO,
                    () -> executeAttempt(command, 1));
        }

        DeterministicScheduler.RunResult schedulerResult = scheduler.runUntilIdle(taskBudget);
        long expectedValue = scenario.stream().mapToLong(IncrementCommand::amount).sum();
        boolean allRequestsSucceeded = completedCommands.size() == scenario.size();
        return new SimulationReport(
                List.copyOf(history),
                schedulerResult.executionTrace(),
                expectedValue,
                store.value(),
                store.value() == expectedValue,
                schedulerResult.drained() && allRequestsSucceeded,
                allRequestsSucceeded);
    }

    private void executeAttempt(IncrementCommand command, int attempt) {
        append(command, attempt, HistoryEvent.Type.INVOKED, store.value());
        CounterStore.ApplyResult result = store.apply(command);
        append(command, attempt,
                result.applied() ? HistoryEvent.Type.EFFECT_APPLIED : HistoryEvent.Type.DUPLICATE_SUPPRESSED,
                result.valueAfter());

        if (failurePlan.timesOut(command.commandId(), attempt)) {
            append(command, attempt, HistoryEvent.Type.TIMED_OUT, result.valueAfter());
            scheduler.schedule("retry:" + command.commandId(), Duration.ofMillis(1),
                    () -> executeAttempt(command, attempt + 1));
            return;
        }

        completedCommands.add(command.commandId());
        append(command, attempt, HistoryEvent.Type.SUCCEEDED, result.valueAfter());
    }

    private void append(IncrementCommand command, int attempt, HistoryEvent.Type type, long valueAfter) {
        history.add(new HistoryEvent(
                historySequence++, clock.instant(), command.commandId(), attempt, type, valueAfter));
    }

    private static void rejectDuplicateLogicalIds(List<IncrementCommand> commands) {
        Set<String> ids = new HashSet<>();
        for (IncrementCommand command : commands) {
            if (!ids.add(command.commandId())) {
                throw new IllegalArgumentException("logical command id must be unique in the scenario");
            }
        }
    }

    public record SimulationReport(
            List<HistoryEvent> history,
            List<String> schedulerTrace,
            long expectedValue,
            long actualValue,
            boolean safetyPreserved,
            boolean livenessPreserved,
            boolean allRequestsSucceeded) {

        public SimulationReport {
            history = List.copyOf(history);
            schedulerTrace = List.copyOf(schedulerTrace);
        }
    }
}
