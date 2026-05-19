package pl.jakubtworek.backend_systems_lab_stage_1.block_a.threads.thread_confinement;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/**
 * Order processor using thread confinement.
 *
 * Instead of protecting shared state with synchronization,
 * all modifications are delegated to a single worker thread.
 */
public class ConfinedOrderProcessor {

    /**
     * Single-thread executor.
     *
     * All submitted tasks are executed sequentially
     * by exactly one worker thread.
     */
    private final ExecutorService executor =
            Executors.newSingleThreadExecutor();

    /**
     * Mutable state used to count processed orders.
     *
     * This variable is not synchronized or volatile,
     * but it is only modified inside tasks executed
     * by the executor's single worker thread.
     */
    private int processed = 0;

    /**
     * Submits an order for processing.
     *
     * The increment of `processed` is executed inside
     * the executor thread, which ensures that only one
     * thread ever mutates this variable.
     */
    public Future<?> submitOrder(Order order) {
        return executor.submit(() -> {
            processed++; // safe: executed only by executor thread
        });
    }

    /**
     * Returns the current number of processed orders.
     *
     * The returned Future from submitOrder() can be used
     * to wait until the increment has completed.
     * Calling Future.get() guarantees that the task
     * finished before continuing.
     */
    public int getProcessed() {
        return processed;
    }

    /**
     * Gracefully stops the executor.
     *
     * No new tasks will be accepted, but already submitted
     * tasks will still complete.
     */
    public void shutdown() {
        executor.shutdown();
    }
}