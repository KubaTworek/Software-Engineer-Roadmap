package pl.jakubtworek.backend_engineering.stage_1.block_a.testing;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.TimeUnit;

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

        if (threads <= 0) {
            throw new IllegalArgumentException("threads must be greater than zero");
        }

        CountDownLatch startGate = new CountDownLatch(1);
        CountDownLatch endGate = new CountDownLatch(threads);
        ConcurrentLinkedQueue<Throwable> failures = new ConcurrentLinkedQueue<>();

        for (int i = 0; i < threads; i++) {
            new Thread(() -> {
                try {
                    startGate.await();
                    task.run();
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    failures.add(exception);
                } catch (Throwable failure) {
                    failures.add(failure);
                } finally {
                    endGate.countDown();
                }
            }, "concurrency-test-worker-" + i).start();
        }

        startGate.countDown();

        if (!endGate.await(5, TimeUnit.SECONDS)) {
            throw new AssertionError("Concurrent test workers did not finish within five seconds");
        }

        if (!failures.isEmpty()) {
            AssertionError assertionError = new AssertionError("Concurrent worker failed");
            failures.forEach(assertionError::addSuppressed);
            throw assertionError;
        }
    }
}
