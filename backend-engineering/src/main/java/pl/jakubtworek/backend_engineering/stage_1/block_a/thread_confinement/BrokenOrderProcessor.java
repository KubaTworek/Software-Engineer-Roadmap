package pl.jakubtworek.backend_engineering.stage_1.block_a.thread_confinement;

/**
 * Incorrect implementation of an order counter.
 *
 * The field `processed` is shared between multiple threads,
 * but access to it is not synchronized.
 */
public class BrokenOrderProcessor {

    /**
     * Shared mutable state.
     *
     * Multiple threads modify this variable concurrently,
     * which leads to race conditions.
     */
    private int processed = 0;

    /**
     * Simulates submitting an order.
     *
     * Problem:
     * The operation `processed++` is not atomic.
     *
     * It consists of three steps:
     * 1. read the current value
     * 2. increment the value
     * 3. write the new value
     *
     * If two threads execute this method at the same time,
     * one update may overwrite the other.
     */
    public void submitOrder(Order order) {
        processed++; // non-atomic update
    }

    /**
     * Returns the number of processed orders.
     *
     * Problem:
     * Without synchronization there is no guarantee that
     * the reading thread sees the latest value written
     * by other threads.
     */
    public int getProcessed() {
        return processed;
    }
}