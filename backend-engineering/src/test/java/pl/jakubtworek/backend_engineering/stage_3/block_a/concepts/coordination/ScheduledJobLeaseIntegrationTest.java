package pl.jakubtworek.backend_engineering.stage_3.block_a.concepts.coordination;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import pl.jakubtworek.backend_engineering.stage_1.block_a.temporal_correctness.FencedJobExecutionLedger;
import pl.jakubtworek.backend_engineering.stage_1.block_a.temporal_correctness.InMemoryScheduleStore;
import pl.jakubtworek.backend_engineering.stage_1.block_a.temporal_correctness.RecurringJobDefinition;
import pl.jakubtworek.backend_engineering.stage_1.block_a.temporal_correctness.RecurringJobScheduler;
import pl.jakubtworek.backend_engineering.stage_1.block_a.temporal_correctness.ScheduledJobRun;
import org.junit.jupiter.api.Test;

class ScheduledJobLeaseIntegrationTest {

    @Test
    void fencingRejectsCompletionFromAWorkerWhoseSchedulerLeaseExpired() {
        MutableClock authorityClock = new MutableClock(Instant.parse("2026-05-01T00:00:00Z"));
        InMemoryLeaseCoordinator coordinator = new InMemoryLeaseCoordinator(authorityClock);
        Lease workerALease = coordinator.tryAcquire("scheduler/settlement", "worker-a", Duration.ofSeconds(5))
                .orElseThrow();
        RecurringJobDefinition job = new RecurringJobDefinition(
                "settlement",
                authorityClock.instant(),
                Duration.ofDays(1),
                RecurringJobDefinition.MisfirePolicy.FIRE_ONCE,
                1
        );
        ScheduledJobRun workerARun = new RecurringJobScheduler(authorityClock, new InMemoryScheduleStore())
                .poll(job, workerALease.fencingToken())
                .getFirst();
        FencedJobExecutionLedger ledger = new FencedJobExecutionLedger();
        assertThat(ledger.start(workerARun)).isEqualTo(FencedJobExecutionLedger.StartDecision.STARTED);

        authorityClock.advance(Duration.ofSeconds(6));
        Lease workerBLease = coordinator.tryAcquire("scheduler/settlement", "worker-b", Duration.ofSeconds(5))
                .orElseThrow();
        ScheduledJobRun workerBRun = new ScheduledJobRun(
                workerARun.jobName(), workerARun.scheduledAt(), workerARun.executionKey(), workerBLease.fencingToken());

        assertThat(ledger.start(workerBRun)).isEqualTo(FencedJobExecutionLedger.StartDecision.STARTED);
        ledger.complete(workerBRun, "committed-by-worker-b");

        assertThatThrownBy(() -> ledger.complete(workerARun, "late-write-from-worker-a"))
                .isInstanceOf(FencedJobExecutionLedger.StaleJobOwnerException.class);
        assertThat(ledger.outcome(workerARun.executionKey())).contains("committed-by-worker-b");
    }

    private static final class MutableClock extends Clock {
        private Instant instant;

        private MutableClock(Instant instant) {
            this.instant = instant;
        }

        void advance(Duration duration) {
            instant = instant.plus(duration);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return Clock.fixed(instant, zone);
        }

        @Override
        public Instant instant() {
            return instant;
        }
    }
}
