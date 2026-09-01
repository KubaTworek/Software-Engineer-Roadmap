package pl.jakubtworek.backend_engineering.stage_1.block_a.executor_service;

import org.junit.jupiter.api.Test;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests demonstrate behavior of different rejection policies
 * under real saturation conditions.
 *
 * Important:
 * To trigger rejection, tasks must block long enough
 * so that the queue actually fills.
 */
class ExecutorPolicyTest {

    /**
     * AbortPolicy:
     * When pool and queue are full,
     * new tasks should be rejected.
     */
    @Test
    void abortPolicyShouldRejectWhenQueueIsFull() throws InterruptedException {

        ThreadPoolExecutor executor =
                ExecutorConfigurations.boundedAbortPolicy(
                        1, // core
                        1, // max
                        1  // queue capacity
                );

        CountDownLatch workerStarted = new CountDownLatch(1);
        CountDownLatch releaseWorker = new CountDownLatch(1);
        try {
            executor.execute(blockingTask(workerStarted, releaseWorker));
            assertTrue(workerStarted.await(1, TimeUnit.SECONDS));
            executor.execute(() -> { }); // fills the only queue slot

            assertThrows(RejectedExecutionException.class,
                    () -> executor.execute(() -> { }));
        } finally {
            releaseWorker.countDown();
            shutdown(executor);
        }
    }

    /**
     * CallerRunsPolicy:
     * When saturated, producer thread executes the task.
     *
     * This creates natural backpressure.
     */
    @Test
    void callerRunsPolicyShouldExecuteRejectedTaskOnSubmittingThread() throws InterruptedException {

        ThreadPoolExecutor executor =
                ExecutorConfigurations.boundedCallerRunsPolicy(
                        1,
                        1,
                        1
                );

        CountDownLatch workerStarted = new CountDownLatch(1);
        CountDownLatch releaseWorker = new CountDownLatch(1);
        AtomicReference<Thread> executionThread = new AtomicReference<>();
        Thread submittingThread = Thread.currentThread();
        try {
            executor.execute(blockingTask(workerStarted, releaseWorker));
            assertTrue(workerStarted.await(1, TimeUnit.SECONDS));
            executor.execute(() -> { }); // fills the only queue slot

            executor.execute(() -> executionThread.set(Thread.currentThread()));

            assertSame(submittingThread, executionThread.get(),
                    "CallerRunsPolicy should execute work synchronously on the producer thread");
        } finally {
            releaseWorker.countDown();
            shutdown(executor);
        }
    }

    @Test
    void shouldRejectInvalidPoolConfiguration() {
        assertThrows(IllegalArgumentException.class,
                () -> ExecutorConfigurations.boundedAbortPolicy(0, 1, 1));
        assertThrows(IllegalArgumentException.class,
                () -> ExecutorConfigurations.boundedAbortPolicy(2, 1, 1));
        assertThrows(IllegalArgumentException.class,
                () -> ExecutorConfigurations.boundedAbortPolicy(1, 1, 0));
    }

    private static Runnable blockingTask(CountDownLatch started, CountDownLatch release) {
        return () -> {
            started.countDown();
            try {
                release.await();
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
            }
        };
    }

    private static void shutdown(ThreadPoolExecutor executor) throws InterruptedException {
        executor.shutdownNow();
        assertTrue(executor.awaitTermination(1, TimeUnit.SECONDS),
                "Executor should terminate after the test releases its worker");
    }
}
