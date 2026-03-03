package pl.jakubtworek.backend_systems_lab_stage_1.block_a.testing;

import java.util.concurrent.CountDownLatch;

/**
 * Helper for deterministic concurrent testing.
 *
 * Uses:
 *  - start gate (all threads start at same time)
 *  - end gate (wait for all to finish)
 *
 * This maximizes probability of race conditions.
 */
public class ConcurrentTestHelper {

    public static void runConcurrent(int threads, Runnable task)
            throws InterruptedException {

        CountDownLatch startGate = new CountDownLatch(1);
        CountDownLatch endGate = new CountDownLatch(threads);

        for (int i = 0; i < threads; i++) {
            new Thread(() -> {
                try {
                    startGate.await(); // wait for simultaneous start
                    task.run();
                } catch (InterruptedException ignored) {
                } finally {
                    endGate.countDown();
                }
            }).start();
        }

        startGate.countDown(); // release all threads
        endGate.await();       // wait for completion
    }
}