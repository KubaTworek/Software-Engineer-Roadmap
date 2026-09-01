package pl.jakubtworek.backend_engineering.stage_3.block_a.concepts;

import org.junit.jupiter.api.Test;
import pl.jakubtworek.backend_engineering.stage_3.block_a.concepts.resilience.RemoteTimeoutException;
import pl.jakubtworek.backend_engineering.stage_3.block_a.concepts.resilience.TimeoutExecutor;

import java.time.Duration;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TimeoutExecutorTest {

    @Test
    void rejectsInvalidTimeoutBeforeSubmittingWork() {
        AtomicBoolean called = new AtomicBoolean();
        try (TimeoutExecutor executor = new TimeoutExecutor(Executors.newSingleThreadExecutor())) {
            assertThatThrownBy(() -> executor.execute(() -> {
                called.set(true);
                return "result";
            }, Duration.ZERO)).isInstanceOf(IllegalArgumentException.class);
        }

        assertThat(called).isFalse();
    }

    @Test
    void cancelsOperationWhenTimeoutExpires() {
        AtomicBoolean interrupted = new AtomicBoolean();
        try (TimeoutExecutor executor = new TimeoutExecutor(Executors.newSingleThreadExecutor())) {
            assertThatThrownBy(() -> executor.execute(() -> {
                try {
                    Thread.sleep(5_000);
                } catch (InterruptedException exception) {
                    interrupted.set(true);
                    throw exception;
                }
                return "too late";
            }, Duration.ofMillis(20))).isInstanceOf(RemoteTimeoutException.class);
        }

        assertThat(interrupted).isTrue();
    }
}
