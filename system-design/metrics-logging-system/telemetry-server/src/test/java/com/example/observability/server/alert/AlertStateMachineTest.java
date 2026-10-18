package com.example.observability.server.alert;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class AlertStateMachineTest {
    private final AlertStateMachine machine = new AlertStateMachine();
    private final Instant now = Instant.parse("2026-01-01T00:00:00Z");

    @Test
    void requiresConditionToHoldForConfiguredDuration() {
        var pending = machine.next(null, true, now, Duration.ofMinutes(2));
        var firing = machine.next(pending, true, now.plusSeconds(120), Duration.ofMinutes(2));
        var repeated = machine.next(firing, true, now.plusSeconds(180), Duration.ofMinutes(2));

        assertThat(pending.name()).isEqualTo("PENDING");
        assertThat(firing.notificationRequired()).isTrue();
        assertThat(repeated.notificationRequired()).isFalse();
    }

    @Test
    void emitsSingleResolutionAfterFiring() {
        var firing = new AlertStateMachine.State("FIRING", now.minusSeconds(60), false);
        assertThat(machine.next(firing, false, now, Duration.ZERO).notificationRequired()).isTrue();
    }
}
