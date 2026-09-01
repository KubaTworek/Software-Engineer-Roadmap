package pl.jakubtworek.backend_engineering.stage_1.block_a.temporal_correctness;

import java.time.Clock;
import java.util.List;
import java.util.Objects;

/** Plans work from a shared checkpoint; execution remains a separate concern. */
public final class RecurringJobScheduler {

    private final Clock clock;
    private final InMemoryScheduleStore scheduleStore;

    public RecurringJobScheduler(Clock clock, InMemoryScheduleStore scheduleStore) {
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
        this.scheduleStore = Objects.requireNonNull(scheduleStore, "scheduleStore must not be null");
    }

    public List<ScheduledJobRun> poll(RecurringJobDefinition definition, long fencingToken) {
        return scheduleStore.claimDue(definition, clock.instant(), fencingToken);
    }
}
