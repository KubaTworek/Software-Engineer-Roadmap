package pl.jakubtworek.backend_engineering.stage_3.block_a.concepts.correctness;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.PriorityQueue;
import java.util.Random;

/**
 * Single-threaded scheduler which explores a reproducible ordering for tasks due
 * at the same time. The seed and execution trace make a failed scenario replayable.
 */
public final class DeterministicScheduler {

    private final ControlledClock clock;
    private final Random random;
    private final PriorityQueue<ScheduledTask> tasks = new PriorityQueue<>();
    private final List<String> executionTrace = new ArrayList<>();
    private long sequence;

    public DeterministicScheduler(ControlledClock clock, long seed) {
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
        this.random = new Random(seed);
    }

    public void schedule(String label, Duration delay, Runnable action) {
        Objects.requireNonNull(label, "label must not be null");
        Objects.requireNonNull(delay, "delay must not be null");
        Objects.requireNonNull(action, "action must not be null");
        if (delay.isNegative()) {
            throw new IllegalArgumentException("delay must not be negative");
        }
        tasks.add(new ScheduledTask(
                clock.instant().plus(delay),
                random.nextLong(),
                sequence++,
                label,
                action));
    }

    public RunResult runUntilIdle(int taskBudget) {
        if (taskBudget <= 0) {
            throw new IllegalArgumentException("taskBudget must be positive");
        }

        int executed = 0;
        while (!tasks.isEmpty() && executed < taskBudget) {
            ScheduledTask task = tasks.remove();
            clock.advanceTo(task.dueAt());
            executionTrace.add(task.label());
            task.action().run();
            executed++;
        }
        return new RunResult(executed, tasks.isEmpty(), List.copyOf(executionTrace));
    }

    public record RunResult(int executedTasks, boolean drained, List<String> executionTrace) {
        public RunResult {
            executionTrace = List.copyOf(executionTrace);
        }
    }

    private record ScheduledTask(
            Instant dueAt,
            long tieBreaker,
            long sequence,
            String label,
            Runnable action) implements Comparable<ScheduledTask> {

        @Override
        public int compareTo(ScheduledTask other) {
            int byTime = dueAt.compareTo(other.dueAt);
            if (byTime != 0) {
                return byTime;
            }
            int byRandomOrder = Long.compare(tieBreaker, other.tieBreaker);
            return byRandomOrder != 0 ? byRandomOrder : Long.compare(sequence, other.sequence);
        }
    }
}
