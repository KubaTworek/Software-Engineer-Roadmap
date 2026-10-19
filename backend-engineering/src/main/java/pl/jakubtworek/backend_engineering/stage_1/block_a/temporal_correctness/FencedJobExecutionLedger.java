package pl.jakubtworek.backend_engineering.stage_1.block_a.temporal_correctness;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * The protected resource enforces fencing. A lease checked only by the worker
 * cannot stop an old, paused owner from committing after its lease expired.
 */
public final class FencedJobExecutionLedger {

    private final Map<String, Entry> entries = new HashMap<>();
    private final Map<String, Long> highestTokenByJob = new HashMap<>();
    private final Map<String, String> activeExecutionByJob = new HashMap<>();

    public synchronized StartDecision start(ScheduledJobRun run) {
        Objects.requireNonNull(run, "run must not be null");
        long highestJobToken = highestTokenByJob.getOrDefault(run.jobName(), 0L);
        if (run.fencingToken() < highestJobToken) {
            throw new StaleJobOwnerException(run.fencingToken(), highestJobToken);
        }
        if (run.fencingToken() > highestJobToken) {
            highestTokenByJob.put(run.jobName(), run.fencingToken());
            activeExecutionByJob.remove(run.jobName());
        }

        Entry current = entries.get(run.executionKey());
        if (current != null && current.state == State.COMPLETED) {
            return StartDecision.ALREADY_COMPLETED;
        }
        if (current != null && run.fencingToken() == current.highestToken) {
            return StartDecision.ALREADY_RUNNING;
        }
        String activeExecution = activeExecutionByJob.get(run.jobName());
        if (activeExecution != null && !activeExecution.equals(run.executionKey())) {
            return StartDecision.OVERLAP_REJECTED;
        }

        entries.put(run.executionKey(), new Entry(run.fencingToken(), State.RUNNING, null));
        activeExecutionByJob.put(run.jobName(), run.executionKey());
        return StartDecision.STARTED;
    }

    public synchronized void complete(ScheduledJobRun run, String outcome) {
        Objects.requireNonNull(run, "run must not be null");
        Objects.requireNonNull(outcome, "outcome must not be null");
        long highestJobToken = highestTokenByJob.getOrDefault(run.jobName(), 0L);
        if (run.fencingToken() < highestJobToken) {
            throw new StaleJobOwnerException(run.fencingToken(), highestJobToken);
        }
        Entry current = entries.get(run.executionKey());
        if (current == null) {
            throw new IllegalStateException("execution must be started before completion");
        }
        if (run.fencingToken() < current.highestToken) {
            throw new StaleJobOwnerException(run.fencingToken(), current.highestToken);
        }
        entries.put(run.executionKey(), new Entry(run.fencingToken(), State.COMPLETED, outcome));
        activeExecutionByJob.remove(run.jobName(), run.executionKey());
    }

    public synchronized Optional<String> outcome(String executionKey) {
        Entry entry = entries.get(executionKey);
        return entry == null || entry.state != State.COMPLETED
                ? Optional.empty()
                : Optional.of(entry.outcome);
    }

    public enum StartDecision {
        STARTED,
        ALREADY_RUNNING,
        ALREADY_COMPLETED,
        OVERLAP_REJECTED
    }

    private enum State {
        RUNNING,
        COMPLETED
    }

    private static final class Entry {
        private final long highestToken;
        private final State state;
        private final String outcome;

        private Entry(long highestToken, State state, String outcome) {
            this.highestToken = highestToken;
            this.state = state;
            this.outcome = outcome;
        }
    }

    public static final class StaleJobOwnerException extends IllegalStateException {
        public StaleJobOwnerException(long candidate, long accepted) {
            super("fencing token %d is older than %d".formatted(candidate, accepted));
        }
    }
}
