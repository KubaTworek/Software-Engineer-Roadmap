package pl.jakubtworek.backend_engineering.stage_3.block_a.implementation.overload;

import org.junit.jupiter.api.Test;
import pl.jakubtworek.backend_engineering.stage_3.block_a.concepts.resilience.RemoteTimeoutException;
import pl.jakubtworek.backend_engineering.stage_3.block_a.concepts.resilience.TimeoutExecutor;

import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TimeoutCancellationSemanticsTest {

    @Test
    void cooperativeDownstreamShouldStopAfterClientTimeoutInterruptsIt() throws Exception {
        ExecutorService dependencyThread = Executors.newSingleThreadExecutor();
        CountDownLatch finished = new CountDownLatch(1);

        try (TimeoutExecutor timeout = new TimeoutExecutor(dependencyThread)) {
            assertThatThrownBy(() -> timeout.execute(() -> {
                try {
                    Thread.sleep(Duration.ofSeconds(10));
                    return "too-late";
                } finally {
                    finished.countDown();
                }
            }, Duration.ofMillis(100))).isInstanceOf(RemoteTimeoutException.class);

            assertThat(finished.await(1, TimeUnit.SECONDS)).isTrue();
        } finally {
            dependencyThread.shutdownNow();
        }
    }

    @Test
    void clientTimeoutShouldNotPretendThatAnUncooperativeDownstreamWasCancelled() throws Exception {
        ExecutorService dependencyThread = Executors.newSingleThreadExecutor();
        CountDownLatch release = new CountDownLatch(1);
        CountDownLatch finished = new CountDownLatch(1);
        CountDownLatch interruptObserved = new CountDownLatch(1);
        AtomicBoolean stillWorkingAfterInterrupt = new AtomicBoolean();

        try (TimeoutExecutor timeout = new TimeoutExecutor(dependencyThread)) {
            assertThatThrownBy(() -> timeout.execute(() -> {
                try {
                    while (true) {
                        try {
                            if (release.await(20, TimeUnit.MILLISECONDS)) {
                                return "finished-after-client-left";
                            }
                        } catch (InterruptedException ignored) {
                            stillWorkingAfterInterrupt.set(true);
                            interruptObserved.countDown();
                        }
                    }
                } finally {
                    finished.countDown();
                }
            }, Duration.ofMillis(100))).isInstanceOf(RemoteTimeoutException.class);

            assertThat(interruptObserved.await(1, TimeUnit.SECONDS)).isTrue();
            assertThat(stillWorkingAfterInterrupt).isTrue();
            assertThat(finished.getCount()).isEqualTo(1);

            release.countDown();
            assertThat(finished.await(1, TimeUnit.SECONDS)).isTrue();
        } finally {
            release.countDown();
            dependencyThread.shutdownNow();
        }
    }
}
