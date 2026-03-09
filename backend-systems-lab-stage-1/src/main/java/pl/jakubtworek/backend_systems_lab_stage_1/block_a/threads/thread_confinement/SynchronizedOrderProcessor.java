package pl.jakubtworek.backend_systems_lab_stage_1.block_a.threads.thread_confinement;

/**
 * Thread-safe implementation using intrinsic locking.
 *
 * synchronized ensures:
 *
 * 1. Mutual exclusion:
 *    Only one thread can execute submitOrder at a time.
 *
 * 2. Visibility:
 *    Monitor exit establishes happens-before relationship
 *    with subsequent monitor enter on same lock.
 *
 * 3. Ordering:
 *    JVM cannot reorder instructions across monitor boundaries.
 *
 * Therefore:
 *   - processed++ becomes effectively atomic.
 *   - no lost updates.
 *
 * Trade-offs:
 *   - Contention under high concurrency.
 *   - Potential scalability bottleneck.
 *
 * Compared to confinement:
 *   - allows concurrent callers
 *   - but serializes critical section
 */
public class SynchronizedOrderProcessor {

    private int processed = 0;

    public synchronized void submitOrder(Order order) {
        processed++;
    }

    public synchronized int getProcessed() {
        return processed;
    }
}