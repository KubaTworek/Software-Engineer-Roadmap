package pl.jakubtworek.backend_systems_lab_stage_1.block_a.executor_service;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;

/**
 * Demonstrates separation of:
 *
 *   WHAT  → task logic
 *   HOW   → execution policy (ExecutorService)
 *
 * TaskProcessor does not know:
 *   - how many threads exist
 *   - queue strategy
 *   - rejection policy
 *
 * It only defines WHAT should be executed.
 */
public class TaskProcessor {

    public void processTasks(ExecutorService executor, int tasks)
            throws InterruptedException {

        CountDownLatch latch = new CountDownLatch(tasks);

        for (int i = 0; i < tasks; i++) {
            executor.execute(() -> {
                simulateWork();
                latch.countDown();
            });
        }

        latch.await();
        executor.shutdown();
    }

    private void simulateWork() {
        try {
            Thread.sleep(1); // simulate IO / blocking work
        } catch (InterruptedException ignored) {}
    }
}