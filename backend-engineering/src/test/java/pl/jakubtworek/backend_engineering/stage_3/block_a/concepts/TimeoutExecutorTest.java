package pl.jakubtworek.backend_engineering.stage_3.block_a.concepts;

import org.junit.jupiter.api.Test;
import pl.jakubtworek.backend_engineering.stage_3.block_a.concepts.resilience.RemoteTimeoutException;
import pl.jakubtworek.backend_engineering.stage_3.block_a.concepts.resilience.TimeoutExecutor;

import java.time.Duration;
import java.util.concurrent.Executors;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
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
    void cancelsOperationWhenTimeoutExpires() throws InterruptedException {
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch interrupted = new CountDownLatch(1);
        CountDownLatch blocked = new CountDownLatch(1);
        // Start the timeout only after the operation has entered its body. Without
        // this handshake, a busy runner could cancel a task still in the queue:
        // cancellation would be correct, but no running thread would observe it.
        try (var worker = new ThreadPoolExecutor(1, 1, 0, TimeUnit.MILLISECONDS,
                new LinkedBlockingQueue<>()) {
            @Override
            public void execute(Runnable command) {
                super.execute(command);
                try {
                    assertThat(started.await(5, TimeUnit.SECONDS)).as("operation started").isTrue();
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    throw new AssertionError("Interrupted while waiting for operation start", exception);
                }
            }
        }; TimeoutExecutor executor = new TimeoutExecutor(worker)) {
            assertThatThrownBy(() -> executor.execute(() -> {
                try {
                    started.countDown();
                    blocked.await();
                } catch (InterruptedException exception) {
                    interrupted.countDown();
                    throw exception;
                }
                return "too late";
            }, Duration.ofMillis(20))).isInstanceOf(RemoteTimeoutException.class);
            // cancel(true) requests interruption; it does not wait for its handling.
            // Observe it BEFORE close(), so shutdownNow() cannot mask missing cancel().
            assertThat(interrupted.await(5, TimeUnit.SECONDS))
                    .as("timeout interrupts the running operation").isTrue();
        }
    }
}
