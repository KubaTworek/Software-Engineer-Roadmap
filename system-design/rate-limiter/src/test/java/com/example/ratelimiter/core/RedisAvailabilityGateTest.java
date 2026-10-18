package com.example.ratelimiter.core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class RedisAvailabilityGateTest {
    @Test
    void opensAfterRepeatedFailuresAndStopsCallingSaturatedDependency() {
        RedisAvailabilityGate gate = new RedisAvailabilityGate();
        AtomicInteger attempts = new AtomicInteger();

        for (int i = 0; i < 3; i++) {
            assertThatThrownBy(() -> gate.call(() -> {
                attempts.incrementAndGet();
                throw new IllegalStateException("redis down");
            })).isInstanceOf(IllegalStateException.class);
        }

        assertThat(gate.isOpen()).isTrue();
        assertThatThrownBy(() -> gate.call(() -> {
            attempts.incrementAndGet();
            return "not reached";
        })).isInstanceOf(RedisAvailabilityGate.RedisCircuitOpenException.class);
        assertThat(attempts).hasValue(3);
    }
}
