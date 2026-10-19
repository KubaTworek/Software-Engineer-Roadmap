package com.example.demoapi.health;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ApplicationHealthStateTest {

    @Test
    void readinessFollowsStartupAndDrainLifecycle() {
        ApplicationHealthState state = new ApplicationHealthState(() -> true);

        assertThat(state.isReady()).isFalse();
        assertThat(state.isLive()).isTrue();

        state.markStarted();
        assertThat(state.isReady()).isTrue();

        state.markDraining();
        assertThat(state.isReady()).isFalse();
        assertThat(state.isLive()).isTrue();
    }

    @Test
    void readinessAndLivenessFailuresRemainIndependent() {
        ApplicationHealthState readinessFailure = new ApplicationHealthState(() -> true);
        readinessFailure.markStarted();
        readinessFailure.simulateReadinessFailure();

        ApplicationHealthState deadlock = new ApplicationHealthState(() -> true);
        deadlock.markStarted();
        deadlock.simulateDeadlock();

        assertThat(readinessFailure.isReady()).isFalse();
        assertThat(readinessFailure.isLive()).isTrue();
        assertThat(deadlock.isReady()).isTrue();
        assertThat(deadlock.isLive()).isFalse();
    }

    @Test
    void dependencyFailureRemovesReadinessWithoutFailingLiveness() {
        ApplicationHealthState state = new ApplicationHealthState(() -> false);
        state.markStarted();

        assertThat(state.isReady()).isFalse();
        assertThat(state.isLive()).isTrue();
    }
}
