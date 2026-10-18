package com.example.ratelimiter.algorithm;

import static org.assertj.core.api.Assertions.assertThat;
import com.example.ratelimiter.algorithm.simulation.RateLimitAlgorithmLab.*;
import org.junit.jupiter.api.Test;

class RateLimitAlgorithmLabTest {
    @Test void naiveCheckThenCommitLeaksPastTheInvariant() {
        var limiter = new NaiveFixedWindow(1);
        assertThat(limiter.check(1)).isTrue();
        assertThat(limiter.check(1)).isTrue();
        limiter.commit(1); limiter.commit(1);
        assertThat(limiter.used()).isEqualTo(2);
    }

    @Test void fixedWindowIsExactInsideOneWindowButAllowsBoundaryBurst() {
        var limiter = new FixedWindow(2, 1_000);
        assertThat(limiter.allow(999, 1)).isTrue();
        assertThat(limiter.allow(999, 1)).isTrue();
        assertThat(limiter.allow(1_000, 1)).isTrue();
        assertThat(limiter.allow(1_000, 1)).isTrue();
    }

    @Test void slidingLogRemovesOnlyEventsOutsideTheRollingWindow() {
        var limiter = new SlidingWindowLog(2, 1_000);
        assertThat(limiter.allow(100)).isTrue();
        assertThat(limiter.allow(200)).isTrue();
        assertThat(limiter.allow(1_099)).isFalse();
        assertThat(limiter.allow(1_100)).isTrue();
        assertThat(limiter.retainedEntries()).isEqualTo(2);
    }

    @Test void slidingCounterCarriesWeightedUsageAcrossTheBoundary() {
        var limiter = new SlidingWindowCounter(2, 1_000);
        assertThat(limiter.allow(900)).isTrue();
        assertThat(limiter.allow(901)).isTrue();
        assertThat(limiter.allow(1_001)).isFalse();
    }

    @Test void tokenBucketAllowsBurstThenRefillsAndIgnoresClockRollback() {
        var limiter = new TokenBucket(2, 1, 1_000);
        assertThat(limiter.allow(1_000, 2)).isTrue();
        assertThat(limiter.allow(900, 1)).isFalse();
        assertThat(limiter.allow(2_000, 1)).isTrue();
    }

    @Test void leakyBucketRejectsWhenFiniteQueueCannotAbsorbMoreWork() {
        var limiter = new LeakyBucket(2, 1, 0);
        assertThat(limiter.enqueue(0, 2)).isTrue();
        assertThat(limiter.enqueue(0, 1)).isFalse();
        assertThat(limiter.enqueue(1_000, 1)).isTrue();
    }

    @Test void gcraRepresentsBurstWithOneTheoreticalArrivalTimestamp() {
        var limiter = new Gcra(2, 2);
        assertThat(limiter.allow(0)).isTrue();
        assertThat(limiter.allow(0)).isTrue();
        assertThat(limiter.allow(0)).isFalse();
        assertThat(limiter.allow(500)).isTrue();
    }

    @Test void adaptiveConcurrencyReducesInFlightWorkAfterOverload() {
        var limiter = new AdaptiveConcurrencyLimit(4, 10);
        assertThat(limiter.tryAcquire()).isTrue();
        limiter.complete(true);
        assertThat(limiter.limit()).isEqualTo(2);
        assertThat(limiter.tryAcquire()).isTrue();
        assertThat(limiter.tryAcquire()).isTrue();
        assertThat(limiter.tryAcquire()).isFalse();
    }
}
