package pl.jakubtworek.backend_systems_lab_stage_1.block_a.threads.thread_confinement;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/**
 * Thread confinement using single-thread executor.
 *
 * Idea:
 * Instead of synchronizing access to shared state,
 * we eliminate shared mutation entirely.
 *
 * All mutations of `processed` happen in exactly one thread:
 * the worker thread inside the single-thread executor.
 *
 * Why this is safe:
 *
 * 1. No two threads modify `processed` concurrently.
 * 2. Executor guarantees sequential execution of submitted tasks.
 * 3. Future.get() establishes a happens-before relationship:
 *    - task completion happens-before get() returns.
 *
 * Therefore:
 *   No synchronized.
 *   No volatile.
 *   No atomic variables.
 *
 * Trade-off:
 *   - Throughput limited to one thread.
 *   - If workload is heavy CPU-bound, this becomes a bottleneck.
 *
 * This pattern is common in:
 *   - actor systems
 *   - event loops
 *   - order processing pipelines
 */
public class ConfinedOrderProcessor {

    private final ExecutorService executor =
            Executors.newSingleThreadExecutor();

    private int processed = 0;

    public Future<?> submitOrder(Order order) {
        return executor.submit(() -> {
            processed++; // safe: only one thread modifies it
        });
    }

    public int getProcessed() {
        return processed; // safe because writes happen-before future completion
    }

    public void shutdown() {
        executor.shutdown();
    }
}