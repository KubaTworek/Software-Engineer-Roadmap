package pl.jakubtworek.backend_engineering.stage_1.block_a.executor_service;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;

/**
 * Demonstrates separation between:
 *
 *   WHAT  → task logic defined in this class
 *   HOW   → execution strategy provided by ExecutorService
 *
 * TaskProcessor is intentionally executor-agnostic:
 * - it does not control thread count
 * - it does not manage queues
 * - it does not define rejection policies
 *
 * Its responsibility is only to submit tasks and wait until all of them
 * complete. The owner of the executor remains responsible for shutdown.
 */
public class TaskProcessor {

    public void processTasks(ExecutorService executor, int tasks)
            throws InterruptedException {

        if (tasks < 0) {
            throw new IllegalArgumentException("tasks must not be negative");
        }

        // Latch is used to wait until all submitted tasks finish execution.
        // Each task decrements the counter when it completes.
        CountDownLatch latch = new CountDownLatch(tasks);

        // Submit 'tasks' number of jobs to the executor.
        // The executor decides when and on which thread they run.
        for (int i = 0; i < tasks; i++) {
            executor.execute(() -> {
                try {
                    simulateWork();
                } finally {
                    // Completion must be signalled even if task logic fails.
                    latch.countDown();
                }
            });
        }

        // Blocks the calling thread until the latch reaches zero,
        // meaning all submitted tasks completed.
        latch.await();

    }

    /**
     * Simulates short blocking work.
     *
     * Thread.sleep is used intentionally to imitate
     * I/O latency or blocking operations. This helps
     * expose differences between executor configurations
     * (e.g. cached pool creating many threads vs fixed pool
     * queuing tasks).
     */
    private void simulateWork() {
        try {
            Thread.sleep(1);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        }
    }
}
