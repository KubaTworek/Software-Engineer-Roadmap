package pl.jakubtworek.backend_systems_lab_stage_1.block_a.threads.visibility;

/**
 * Thread-safe implementation using volatile to solve visibility problem.
 *
 * ------------------------------------------------------------
 * Why volatile fixes the issue
 * ------------------------------------------------------------
 *
 * Declaring:
 *
 *   private volatile boolean running;
 *
 * establishes special memory semantics.
 *
 * volatile provides:
 *
 * 1. Visibility guarantee
 *
 *    A write to a volatile variable:
 *
 *        running = false;
 *
 *    happens-before every subsequent read of that variable
 *    in other threads.
 *
 *    This ensures:
 *        - No stale cached values
 *        - Immediate visibility across threads
 *
 * 2. Ordering guarantee
 *
 *    The JVM is prohibited from:
 *        - Reordering instructions around volatile reads/writes
 *
 *    Volatile write:
 *        has release semantics
 *
 *    Volatile read:
 *        has acquire semantics
 *
 *    Together they form a happens-before relationship.
 *
 * ------------------------------------------------------------
 * What volatile does NOT provide
 * ------------------------------------------------------------
 *
 * volatile does NOT:
 *
 *   - Provide mutual exclusion
 *   - Make compound operations atomic
 *
 * Example:
 *
 *   count++  // still NOT atomic even if count is volatile
 *
 * Because it is:
 *   read -> modify -> write
 *
 * Volatile only guarantees atomicity for single read/write.
 *
 * ------------------------------------------------------------
 * Why this is the correct tool here
 * ------------------------------------------------------------
 *
 * This is a simple state flag.
 *
 * We only need:
 *   - Visibility
 *   - Ordering
 *
 * We do NOT need:
 *   - Atomic compound operations
 *   - Mutual exclusion
 *
 * Therefore volatile is:
 *   - Correct
 *   - Lightweight
 *   - More efficient than synchronized
 *
 * ------------------------------------------------------------
 * Architectural Insight
 * ------------------------------------------------------------
 *
 * volatile is appropriate for:
 *
 *   - Lifecycle flags
 *   - Shutdown signals
 *   - Publication of immutable references
 *
 * It is NOT a replacement for full synchronization
 * when maintaining invariants across multiple fields.
 */
public class VisibilityExampleVolatile {

    private volatile boolean running = true;

    public void stop() {
        running = false;
    }

    public void work() {
        while (running) {
            // Busy loop
        }
    }

    public boolean isRunning() {
        return running;
    }
}