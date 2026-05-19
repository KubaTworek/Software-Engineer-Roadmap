package pl.jakubtworek.backend_systems_lab_stage_1.block_a.threads.executor_service;

import org.junit.jupiter.api.Test;

import java.util.concurrent.*;

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
    void abortPolicy_shouldRejectWhenQueueFull() {

        ThreadPoolExecutor executor =
                ExecutorConfigurations.boundedAbortPolicy(
                        1, // core
                        1, // max
                        1  // queue capacity
                );

        // 1️⃣ Occupy the single worker thread
        executor.execute(() -> sleep(500));

        // 2️⃣ Fill the queue
        executor.execute(() -> sleep(500));

        // 3️⃣ Now pool=1 busy, queue=1 full → next must reject
        assertThrows(RejectedExecutionException.class,
                () -> executor.execute(() -> sleep(100)));

        executor.shutdown();
    }

    /**
     * CallerRunsPolicy:
     * When saturated, producer thread executes the task.
     *
     * This creates natural backpressure.
     */
    @Test
    void callerRunsPolicy_shouldApplyBackpressure() {

        ThreadPoolExecutor executor =
                ExecutorConfigurations.boundedCallerRunsPolicy(
                        1,
                        1,
                        1
                );

        // Occupy worker
        executor.execute(() -> sleep(500));

        // Fill queue
        executor.execute(() -> sleep(500));

        long start = System.currentTimeMillis();

        // This task should run in calling thread
        executor.execute(() -> sleep(200));

        long duration = System.currentTimeMillis() - start;

        assertTrue(duration >= 200,
                "CallerRunsPolicy should block producer thread");

        executor.shutdown();
    }

    private void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException ignored) {}
    }
}