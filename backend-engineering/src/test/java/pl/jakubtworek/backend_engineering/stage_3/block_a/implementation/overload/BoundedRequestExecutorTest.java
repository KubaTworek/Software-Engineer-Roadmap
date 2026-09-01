package pl.jakubtworek.backend_engineering.stage_3.block_a.implementation.overload;

import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BoundedRequestExecutorTest {

    @Test
    void shouldShedExcessWorkInsteadOfGrowingAnUnboundedQueue() throws Exception {
        CountDownLatch workerStarted = new CountDownLatch(1);
        CountDownLatch releaseWorker = new CountDownLatch(1);

        try (BoundedRequestExecutor executor = new BoundedRequestExecutor(1, 1)) {
            Future<String> active = executor.submit(() -> {
                workerStarted.countDown();
                releaseWorker.await();
                return "active";
            });
            assertThat(workerStarted.await(1, TimeUnit.SECONDS)).isTrue();

            Future<String> queued = executor.submit(() -> "queued");
            assertThat(executor.snapshot().queued()).isEqualTo(1);

            assertThatThrownBy(() -> executor.submit(() -> "must-be-shed"))
                    .isInstanceOf(LoadShedException.class);
            assertThat(executor.snapshot().shed()).isEqualTo(1);

            releaseWorker.countDown();
            assertThat(active.get()).isEqualTo("active");
            assertThat(queued.get()).isEqualTo("queued");
            assertThat(executor.snapshot().accepted()).isEqualTo(2);
        } finally {
            releaseWorker.countDown();
        }
    }
}
