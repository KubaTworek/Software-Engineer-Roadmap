package pl.jakubtworek.backend_engineering.stage_3.block_a.concepts.coordination;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DistributedCoordinationTest {

    @Test
    void expiredOwnerCannotOverwriteANewerOwnerWhenTheResourceEnforcesFencing() {
        MutableClock authorityClock = new MutableClock(Instant.parse("2026-01-01T00:00:00Z"));
        InMemoryLeaseCoordinator coordinator = new InMemoryLeaseCoordinator(authorityClock);
        FencedRegister<String> fencedRegister = new FencedRegister<>();
        AtomicReference<String> unfencedRegister = new AtomicReference<>();

        Lease workerA = coordinator.tryAcquire("invoice-export", "worker-a", Duration.ofSeconds(5))
                .orElseThrow();
        assertThat(coordinator.tryAcquire("invoice-export", "worker-b", Duration.ofSeconds(5)))
                .isEmpty();

        authorityClock.advance(Duration.ofSeconds(5));
        Lease workerB = coordinator.tryAcquire("invoice-export", "worker-b", Duration.ofSeconds(5))
                .orElseThrow();

        assertThat(workerB.fencingToken()).isGreaterThan(workerA.fencingToken());

        // A lock alone cannot stop a paused old process from performing this late write.
        unfencedRegister.set("new result from worker-b");
        unfencedRegister.set("stale result from worker-a");
        assertThat(unfencedRegister).hasValue("stale result from worker-a");

        fencedRegister.write(workerB, "new result from worker-b");
        assertThatThrownBy(() -> fencedRegister.write(workerA, "stale result from worker-a"))
                .isInstanceOf(StaleFencingTokenException.class)
                .hasMessageContaining("fencing token 1 is older than 2");
        assertThat(fencedRegister.value()).contains("new result from worker-b");
    }

    @Test
    void staleTermCannotRenewReleaseOrReplaceTheCurrentLeader() {
        MutableClock clock = new MutableClock(Instant.parse("2026-01-01T00:00:00Z"));
        InMemoryLeaseCoordinator coordinator = new InMemoryLeaseCoordinator(clock);
        LeaderElection election = new LeaderElection(coordinator);

        LeadershipTerm nodeA = election.campaign("outbox-relay", "node-a", Duration.ofSeconds(3))
                .orElseThrow();
        assertThat(election.campaign("outbox-relay", "node-b", Duration.ofSeconds(3))).isEmpty();

        clock.advance(Duration.ofSeconds(3));
        LeadershipTerm nodeB = election.campaign("outbox-relay", "node-b", Duration.ofSeconds(3))
                .orElseThrow();

        assertThat(election.heartbeat(nodeA, Duration.ofSeconds(3))).isEmpty();
        assertThat(coordinator.release(nodeA.asLease())).isFalse();
        assertThat(election.currentLeader("outbox-relay"))
                .contains(nodeB);
        assertThat(nodeB.term()).isEqualTo(nodeA.term() + 1);
    }

    @Test
    void workerClockCanDisagreeWithTheAuthorityAboutLeaseExpiry() {
        MutableClock authorityClock = new MutableClock(Instant.parse("2026-01-01T00:00:00Z"));
        InMemoryLeaseCoordinator coordinator = new InMemoryLeaseCoordinator(authorityClock);
        Lease nodeA = coordinator.tryAcquire("daily-settlement", "node-a", Duration.ofSeconds(5))
                .orElseThrow();

        authorityClock.advance(Duration.ofSeconds(6));
        Instant slowWorkerClock = Instant.parse("2026-01-01T00:00:04Z");

        assertThat(nodeA.isExpiredAt(authorityClock.instant())).isTrue();
        assertThat(nodeA.isExpiredAt(slowWorkerClock)).isFalse();
        assertThat(coordinator.tryAcquire("daily-settlement", "node-b", Duration.ofSeconds(5)))
                .get()
                .extracting(Lease::fencingToken)
                .isEqualTo(2L);
    }

    @Test
    void activeOwnerCanRenewWithoutChangingItsFencingToken() {
        MutableClock clock = new MutableClock(Instant.parse("2026-01-01T00:00:00Z"));
        InMemoryLeaseCoordinator coordinator = new InMemoryLeaseCoordinator(clock);
        Lease original = coordinator.tryAcquire("projection", "worker-a", Duration.ofSeconds(5))
                .orElseThrow();

        clock.advance(Duration.ofSeconds(4));
        Lease renewed = coordinator.renew(original, Duration.ofSeconds(5)).orElseThrow();

        assertThat(renewed.fencingToken()).isEqualTo(original.fencingToken());
        assertThat(renewed.expiresAt()).isEqualTo(Instant.parse("2026-01-01T00:00:09Z"));
    }

    @Test
    void releasingAndReacquiringStartsANewerTermEvenForTheSameOwner() {
        MutableClock clock = new MutableClock(Instant.parse("2026-01-01T00:00:00Z"));
        InMemoryLeaseCoordinator coordinator = new InMemoryLeaseCoordinator(clock);
        Lease first = coordinator.tryAcquire("projection", "worker-a", Duration.ofSeconds(5))
                .orElseThrow();

        assertThat(coordinator.release(first)).isTrue();
        Lease second = coordinator.tryAcquire("projection", "worker-a", Duration.ofSeconds(5))
                .orElseThrow();

        assertThat(second.fencingToken()).isEqualTo(first.fencingToken() + 1);
    }

    private static final class MutableClock extends Clock {
        private Instant current;

        private MutableClock(Instant current) {
            this.current = current;
        }

        void advance(Duration duration) {
            current = current.plus(duration);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return current;
        }
    }
}
