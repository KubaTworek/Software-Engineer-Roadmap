package pl.jakubtworek.backend_systems_lab_stage_1.block_a.threads.race_condition;

import java.util.concurrent.*;

/**
 * Thread-safe implementation using thread confinement.
 *
 * Instead of protecting shared state with locks or atomic operations,
 * this implementation avoids shared mutable state across threads entirely.
 *
 * ------------------------------------------------------------
 * What is thread confinement?
 * ------------------------------------------------------------
 *
 * Thread confinement means:
 *
 *   A piece of mutable state is accessed and modified
 *   by only one thread.
 *
 * If only one thread can modify the state,
 * synchronization is not required.
 *
 * In this implementation:
 *
 *   - All mutations of 'available' and 'sold'
 *     happen inside a single-thread Executor.
 *
 *   - The executor guarantees sequential execution
 *     of submitted tasks.
 *
 * ------------------------------------------------------------
 * Why this works
 * ------------------------------------------------------------
 *
 * Executors.newSingleThreadExecutor() ensures:
 *
 *   - Only one worker thread exists.
 *   - Tasks are executed one-by-one in submission order.
 *
 * Therefore:
 *
 *   buy() operations never execute concurrently.
 *
 * The critical section:
 *
 *   if (available > 0) {
 *       available--;
 *       sold++;
 *   }
 *
 * is effectively serialized.
 *
 * No race condition is possible because:
 *
 *   There is no parallel modification of shared state.
 *
 * ------------------------------------------------------------
 * Why future.get()?
 * ------------------------------------------------------------
 *
 * executor.submit(...) is asynchronous.
 *
 * If we did not call future.get(),
 * buy() would return immediately
 * and state updates might not yet be visible.
 *
 * future.get():
 *   - Blocks until task completes
 *   - Establishes happens-before relationship
 *   - Guarantees visibility of changes
 *
 * ------------------------------------------------------------
 * Memory Model Guarantees
 * ------------------------------------------------------------
 *
 * ExecutorService + Future.get() ensure:
 *
 *   - Proper task publication
 *   - Happens-before from task completion to get() return
 *   - Visibility of writes made inside the task
 *
 * Therefore no additional synchronization is required.
 *
 * ------------------------------------------------------------
 * Architectural perspective
 * ------------------------------------------------------------
 *
 * This pattern is similar to:
 *
 *   - Actor model
 *   - Event loop
 *   - Command queue processing
 *
 * Instead of solving race conditions with locks,
 * we eliminate shared concurrency at the architectural level.
 *
 * ------------------------------------------------------------
 * Trade-offs
 * ------------------------------------------------------------
 *
 * Pros:
 *   - No locks
 *   - No CAS
 *   - No contention
 *   - Simple reasoning about state
 *
 * Cons:
 *   - Throughput limited to single thread
 *   - Blocking caller via future.get()
 *   - Requires lifecycle management (shutdown)
 *
 * This approach is appropriate when:
 *
 *   - State must remain strictly consistent
 *   - Throughput requirements are moderate
 *   - Simplicity is preferred over parallelism
 *
 * In high-load systems,
 * this model can be scaled via sharding:
 *
 *   Multiple single-thread executors,
 *   each owning a partition of state.
 */
public class SingleThreadTicketStore implements TicketStore {

    private int available = 1;
    private int sold = 0;
    private final int initial = 1;

    private final ExecutorService executor =
            Executors.newSingleThreadExecutor();

    @Override
    public void buy() {
        Future<?> future = executor.submit(() -> {
            if (available > 0) {
                available--;
                sold++;
            }
        });

        try {
            future.get();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public int getAvailable() {
        return available;
    }

    @Override
    public int getSold() {
        return sold;
    }

    @Override
    public int getInitial() {
        return initial;
    }

    @Override
    public String name() {
        return "Single-thread Executor";
    }
}