package pl.jakubtworek.backend_engineering.stage_1.block_a.temporal_correctness;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import org.junit.jupiter.api.Test;

class RecurringJobSchedulerTest {

    private static final Instant FIRST_RUN = Instant.parse("2026-04-01T00:00:00Z");

    @Test
    void sharedCheckpointSurvivesSchedulerRestartAndPreventsDuplicateClaim() {
        Clock clock = Clock.fixed(FIRST_RUN, ZoneOffset.UTC);
        InMemoryScheduleStore durableStore = new InMemoryScheduleStore();
        RecurringJobDefinition job = job("daily-report", RecurringJobDefinition.MisfirePolicy.FIRE_ONCE, 1);

        List<ScheduledJobRun> beforeRestart = new RecurringJobScheduler(clock, durableStore).poll(job, 1);
        List<ScheduledJobRun> afterRestart = new RecurringJobScheduler(clock, durableStore).poll(job, 1);

        assertThat(beforeRestart).singleElement()
                .extracting(ScheduledJobRun::executionKey)
                .isEqualTo("daily-report/2026-04-01T00:00:00Z");
        assertThat(afterRestart).isEmpty();
        assertThat(durableStore.nextScheduledAt(job.name())).isEqualTo(FIRST_RUN.plus(Duration.ofHours(1)));
    }

    @Test
    void misfirePolicyMakesRecoveryAfterDowntimeExplicit() {
        Clock afterDowntime = Clock.fixed(
                FIRST_RUN.plus(Duration.ofHours(3)).plus(Duration.ofMinutes(30)), ZoneOffset.UTC);

        InMemoryScheduleStore skipStore = new InMemoryScheduleStore();
        List<ScheduledJobRun> skipped = new RecurringJobScheduler(afterDowntime, skipStore)
                .poll(job("skip", RecurringJobDefinition.MisfirePolicy.SKIP, 1), 1);

        InMemoryScheduleStore onceStore = new InMemoryScheduleStore();
        List<ScheduledJobRun> firedOnce = new RecurringJobScheduler(afterDowntime, onceStore)
                .poll(job("once", RecurringJobDefinition.MisfirePolicy.FIRE_ONCE, 1), 1);

        InMemoryScheduleStore catchUpStore = new InMemoryScheduleStore();
        RecurringJobScheduler catchUpScheduler = new RecurringJobScheduler(afterDowntime, catchUpStore);
        RecurringJobDefinition catchUp = job("catch-up", RecurringJobDefinition.MisfirePolicy.CATCH_UP_BOUNDED, 2);
        List<ScheduledJobRun> firstBatch = catchUpScheduler.poll(catchUp, 1);
        List<ScheduledJobRun> secondBatch = catchUpScheduler.poll(catchUp, 1);

        assertThat(skipped).isEmpty();
        assertThat(skipStore.nextScheduledAt("skip")).isEqualTo(FIRST_RUN.plus(Duration.ofHours(4)));
        assertThat(firedOnce).extracting(ScheduledJobRun::scheduledAt)
                .containsExactly(FIRST_RUN.plus(Duration.ofHours(3)));
        assertThat(firstBatch).extracting(ScheduledJobRun::scheduledAt)
                .containsExactly(FIRST_RUN, FIRST_RUN.plus(Duration.ofHours(1)));
        assertThat(secondBatch).extracting(ScheduledJobRun::scheduledAt)
                .containsExactly(FIRST_RUN.plus(Duration.ofHours(2)), FIRST_RUN.plus(Duration.ofHours(3)));
    }

    @Test
    void concurrentPollersCannotClaimTheSameScheduledSlot() {
        Clock clock = Clock.fixed(FIRST_RUN, ZoneOffset.UTC);
        InMemoryScheduleStore store = new InMemoryScheduleStore();
        RecurringJobDefinition job = job("settlement", RecurringJobDefinition.MisfirePolicy.FIRE_ONCE, 1);
        CountDownLatch start = new CountDownLatch(1);

        CompletableFuture<List<ScheduledJobRun>> first = CompletableFuture.supplyAsync(
                () -> pollAfter(start, new RecurringJobScheduler(clock, store), job));
        CompletableFuture<List<ScheduledJobRun>> second = CompletableFuture.supplyAsync(
                () -> pollAfter(start, new RecurringJobScheduler(clock, store), job));
        start.countDown();

        List<ScheduledJobRun> all = java.util.stream.Stream.concat(first.join().stream(), second.join().stream()).toList();

        assertThat(all).singleElement();
        assertThat(all).extracting(ScheduledJobRun::executionKey).doesNotHaveDuplicates();
    }

    @Test
    void ledgerDeduplicatesOneSlotAndForbidsOverlappingRunsOfTheSameJob() {
        FencedJobExecutionLedger ledger = new FencedJobExecutionLedger();
        ScheduledJobRun first = ScheduledJobRun.create("billing", FIRST_RUN, 7);
        ScheduledJobRun next = ScheduledJobRun.create("billing", FIRST_RUN.plus(Duration.ofHours(1)), 7);

        assertThat(ledger.start(first)).isEqualTo(FencedJobExecutionLedger.StartDecision.STARTED);
        assertThat(ledger.start(first)).isEqualTo(FencedJobExecutionLedger.StartDecision.ALREADY_RUNNING);
        assertThat(ledger.start(next)).isEqualTo(FencedJobExecutionLedger.StartDecision.OVERLAP_REJECTED);

        ledger.complete(first, "invoice-101");

        assertThat(ledger.start(first)).isEqualTo(FencedJobExecutionLedger.StartDecision.ALREADY_COMPLETED);
        assertThat(ledger.start(next)).isEqualTo(FencedJobExecutionLedger.StartDecision.STARTED);
    }

    private static List<ScheduledJobRun> pollAfter(
            CountDownLatch start,
            RecurringJobScheduler scheduler,
            RecurringJobDefinition job
    ) {
        try {
            start.await();
            return scheduler.poll(job, 1);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("test interrupted", exception);
        }
    }

    private static RecurringJobDefinition job(
            String name,
            RecurringJobDefinition.MisfirePolicy policy,
            int maximumCatchUpRuns
    ) {
        return new RecurringJobDefinition(name, FIRST_RUN, Duration.ofHours(1), policy, maximumCatchUpRuns);
    }
}
