package pl.jakubtworek.backend_systems_lab_stage_1.block_a.threads.thread_confinement;

/**
 * Broken implementation.
 *
 * Problem:
 * `processed++` is NOT atomic.
 *
 * It expands to three operations:
 *
 *   1. read processed
 *   2. increment
 *   3. write processed
 *
 * If two threads execute this concurrently:
 *
 *   Thread A reads 5
 *   Thread B reads 5
 *   Thread A writes 6
 *   Thread B writes 6
 *
 * One increment is lost (lost update).
 *
 * There is:
 *   - no mutual exclusion
 *   - no happens-before relationship
 *   - no visibility guarantee
 *
 * This class is NOT thread-safe.
 */
public class BrokenOrderProcessor {

    private int processed = 0;

    public void submitOrder(Order order) {
        processed++; // race condition
    }

    public int getProcessed() {
        return processed;
    }
}