package com.ridesharing.mvp.ride;

import com.ridesharing.mvp.common.ApiException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

/**
 * Dowodzi safety maszyny stanów przejazdu. Retry tej samej komendy jest
 * idempotentne, ale stan końcowy nie może wrócić do aktywnego matchingu.
 */
class RideStateMachineTest {

    private final RideStateMachine stateMachine = new RideStateMachine();

    @Test
    void acceptsTheCanonicalRideLifecycleAndIdempotentRetry() {
        assertThatCode(() -> {
            stateMachine.assertTransition(RideStatus.REQUESTED, RideStatus.MATCHING);
            stateMachine.assertTransition(RideStatus.MATCHING, RideStatus.DRIVER_ASSIGNED);
            stateMachine.assertTransition(RideStatus.DRIVER_ASSIGNED, RideStatus.DRIVER_ARRIVING);
            stateMachine.assertTransition(RideStatus.DRIVER_ARRIVING, RideStatus.DRIVER_ARRIVED);
            stateMachine.assertTransition(RideStatus.DRIVER_ARRIVED, RideStatus.IN_PROGRESS);
            stateMachine.assertTransition(RideStatus.IN_PROGRESS, RideStatus.COMPLETED);
            stateMachine.assertTransition(RideStatus.COMPLETED, RideStatus.COMPLETED);
        }).doesNotThrowAnyException();
    }

    @Test
    void terminalRideCannotReturnToMatching() {
        assertThatThrownBy(() -> stateMachine.assertTransition(RideStatus.COMPLETED, RideStatus.MATCHING))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("Invalid ride transition");
    }

    @ParameterizedTest
    @EnumSource(value = RideStatus.class, names = {
            "COMPLETED", "CANCELLED_BY_PASSENGER", "CANCELLED_BY_DRIVER", "EXPIRED", "FAILED"
    })
    void everyTerminalStateRejectsEveryDifferentTarget(RideStatus terminal) {
        for (RideStatus target : RideStatus.values()) {
            if (target == terminal) {
                assertThatCode(() -> stateMachine.assertTransition(terminal, target)).doesNotThrowAnyException();
            } else {
                assertThatThrownBy(() -> stateMachine.assertTransition(terminal, target))
                        .isInstanceOf(ApiException.class);
            }
        }
    }
}
