package pl.jakubtworek.backend_systems_lab_stage_1.block_a.threads.testing;

import java.util.concurrent.CountDownLatch;

/**
 * Helper class used in tests to run a given task concurrently
 * in multiple threads. It uses two synchronization gates:
 *
 * startGate – ensures that all threads begin execution at roughly
 * the same moment.
 *
 * endGate – allows the calling thread to wait until all worker
 * threads have finished execution.
 */
public class ConcurrentTestHelper {

    /**
     * Executes the provided task concurrently in the specified
     * number of threads.
     *
     * @param threads number of worker threads
     * @param task operation executed by every thread
     * @throws InterruptedException if the waiting thread is interrupted
     */
    public static void runConcurrent(int threads, Runnable task)
            throws InterruptedException {

        // Latch used as a starting barrier. All worker threads wait here
        // until the main thread releases them.
        CountDownLatch startGate = new CountDownLatch(1);

        // Latch used to detect when all worker threads complete execution.
        CountDownLatch endGate = new CountDownLatch(threads);

        for (int i = 0; i < threads; i++) {
            // Create a worker thread that will execute the given task
            new Thread(() -> {
                try {
                    // Wait until the main thread releases the start gate
                    // so that all threads begin execution together.
                    startGate.await();

                    // Execute the tested operation.
                    task.run();

                } catch (InterruptedException ignored) {
                    // Interrupted threads are ignored in this testing helper.
                } finally {
                    // Signal that this thread has finished its work.
                    endGate.countDown();
                }
            }).start();
        }

        // Release all threads waiting on startGate.
        startGate.countDown();

        // Wait until every worker thread finishes execution.
        endGate.await();
    }
}