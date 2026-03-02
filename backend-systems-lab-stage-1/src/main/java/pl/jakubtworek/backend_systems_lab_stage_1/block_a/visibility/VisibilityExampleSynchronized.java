package pl.jakubtworek.backend_systems_lab_stage_1.block_a.visibility;

/**
 * Thread-safe implementation using intrinsic locking (synchronized).
 *
 * ------------------------------------------------------------
 * Why synchronized fixes the visibility problem
 * ------------------------------------------------------------
 *
 * synchronized provides:
 *
 * 1. Mutual exclusion
 *    Only one thread can execute a synchronized method
 *    guarded by the same monitor at a time.
 *
 * 2. Visibility guarantees (Java Memory Model)
 *
 *    Exiting a synchronized block (monitor unlock)
 *    establishes a happens-before relationship with
 *    the next successful acquisition (monitor lock)
 *    of the same monitor.
 *
 *    This means:
 *
 *      - All writes made inside stop()
 *        become visible to threads calling isRunning()
 *
 *      - No stale cached values are allowed.
 *
 * 3. Ordering guarantees
 *
 *    The JVM cannot reorder instructions across
 *    monitor boundaries.
 *
 *    Monitor enter → acquire semantics
 *    Monitor exit  → release semantics
 *
 * ------------------------------------------------------------
 * Why work() calls isRunning()
 * ------------------------------------------------------------
 *
 * The worker thread repeatedly calls:
 *
 *     while (isRunning()) { }
 *
 * Each call to isRunning() is synchronized.
 *
 * Therefore:
 *
 *   - The thread re-acquires the monitor on every iteration
 *   - It must observe the most recent write to running
 *
 * Once stop() executes and releases the monitor,
 * the worker thread's next isRunning() call
 * will see running = false.
 *
 * ------------------------------------------------------------
 * Difference vs volatile
 * ------------------------------------------------------------
 *
 * volatile:
 *   - Provides visibility
 *   - Provides ordering
 *   - Does NOT provide mutual exclusion
 *
 * synchronized:
 *   - Provides visibility
 *   - Provides ordering
 *   - Provides mutual exclusion
 *
 * In this specific case, mutual exclusion is not required,
 * but synchronized guarantees correctness as a side effect.
 *
 * ------------------------------------------------------------
 * Trade-offs
 * ------------------------------------------------------------
 *
 * Pros:
 *   - Strong guarantees
 *   - Simple reasoning
 *   - Works for compound invariants
 *
 * Cons:
 *   - Higher overhead than volatile
 *   - Monitor acquisition on every loop iteration
 *   - Can reduce scalability
 *
 * This approach is appropriate when:
 *   - Multiple variables form an invariant
 *   - Future modifications may require stronger guarantees
 *
 * For simple lifecycle flags, volatile is usually sufficient.
 */
public class VisibilityExampleSynchronized {

    private boolean running = true;

    public synchronized void stop() {
        running = false;
    }

    public synchronized boolean isRunning() {
        return running;
    }

    public void work() {
        while (isRunning()) {
            // Busy loop
        }
    }
}