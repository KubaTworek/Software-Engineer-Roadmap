package pl.jakubtworek.backend_engineering.stage_1.block_a.temporal_correctness;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * A durable-store model shared by scheduler instances. The synchronized claim
 * represents one database transaction with a unique execution key.
 */
public final class InMemoryScheduleStore {

    private final Map<String, Instant> nextScheduledAt = new HashMap<>();
    private final Set<String> claimedExecutionKeys = new HashSet<>();

    public synchronized List<ScheduledJobRun> claimDue(
            RecurringJobDefinition definition,
            Instant now,
            long fencingToken
    ) {
        if (fencingToken <= 0) {
            throw new IllegalArgumentException("fencingToken must be positive");
        }
        Instant next = nextScheduledAt.computeIfAbsent(
                definition.name(), ignored -> definition.firstScheduledAt());
        if (now.isBefore(next)) {
            return List.of();
        }

        List<Instant> selected = selectSlots(definition, next, now);
        Instant checkpoint = nextCheckpoint(definition, next, now, selected);
        nextScheduledAt.put(definition.name(), checkpoint);

        List<ScheduledJobRun> claimed = new ArrayList<>(selected.size());
        for (Instant slot : selected) {
            ScheduledJobRun run = ScheduledJobRun.create(definition.name(), slot, fencingToken);
            if (claimedExecutionKeys.add(run.executionKey())) {
                claimed.add(run);
            }
        }
        return List.copyOf(claimed);
    }

    public synchronized Instant nextScheduledAt(String jobName) {
        Instant next = nextScheduledAt.get(jobName);
        if (next == null) {
            throw new IllegalArgumentException("unknown job " + jobName);
        }
        return next;
    }

    private static List<Instant> selectSlots(
            RecurringJobDefinition definition,
            Instant next,
            Instant now
    ) {
        if (now.equals(next)) {
            return List.of(next);
        }

        long intervalsBehind = java.time.Duration.between(next, now).dividedBy(definition.interval());
        return switch (definition.misfirePolicy()) {
            case SKIP -> List.of();
            case FIRE_ONCE -> List.of(next.plus(definition.interval().multipliedBy(intervalsBehind)));
            case CATCH_UP_BOUNDED -> {
                int count = (int) Math.min(intervalsBehind + 1, definition.maximumCatchUpRuns());
                List<Instant> slots = new ArrayList<>(count);
                for (int index = 0; index < count; index++) {
                    slots.add(next.plus(definition.interval().multipliedBy(index)));
                }
                yield slots;
            }
        };
    }

    private static Instant nextCheckpoint(
            RecurringJobDefinition definition,
            Instant previousNext,
            Instant now,
            List<Instant> selected
    ) {
        if (now.equals(previousNext)) {
            return previousNext.plus(definition.interval());
        }
        if (definition.misfirePolicy() == RecurringJobDefinition.MisfirePolicy.CATCH_UP_BOUNDED) {
            return previousNext.plus(definition.interval().multipliedBy(selected.size()));
        }
        long intervalsBehind = java.time.Duration.between(previousNext, now).dividedBy(definition.interval());
        return previousNext.plus(definition.interval().multipliedBy(intervalsBehind + 1));
    }
}
