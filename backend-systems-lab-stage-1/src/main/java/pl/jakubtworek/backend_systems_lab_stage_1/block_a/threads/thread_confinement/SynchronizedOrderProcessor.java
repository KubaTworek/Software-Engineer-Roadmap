package pl.jakubtworek.backend_systems_lab_stage_1.block_a.threads.thread_confinement;

/**
 * Order processor using intrinsic locking.
 *
 * Access to the shared counter is protected
 * by the object's intrinsic lock.
 */
public class SynchronizedOrderProcessor {

    /**
     * Shared mutable counter.
     *
     * Multiple threads may access this field,
     * therefore access must be synchronized.
     */
    private int processed = 0;

    /**
     * Registers a processed order.
     *
     * The synchronized keyword ensures that only one
     * thread at a time can execute this method on
     * the same instance.
     *
     * As a result, the increment operation cannot
     * overlap with increments from other threads.
     */
    public synchronized void submitOrder(Order order) {
        processed++; // protected by intrinsic lock
    }

    /**
     * Returns the current number of processed orders.
     *
     * Synchronization guarantees that the reading thread
     * sees the most recent value written by other threads
     * that previously exited synchronized blocks on
     * the same object.
     */
    public synchronized int getProcessed() {
        return processed;
    }
}