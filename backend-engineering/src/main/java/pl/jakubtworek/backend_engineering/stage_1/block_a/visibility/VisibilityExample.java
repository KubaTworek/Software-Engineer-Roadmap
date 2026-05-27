package pl.jakubtworek.backend_engineering.stage_1.block_a.visibility;

/**
 * Example showing that a field shared between threads
 * may not be immediately visible without proper synchronization.
 *
 * In this class the field `running` is modified by one thread
 * and read by another thread inside a loop.
 */
public class VisibilityExample {

    // Shared flag controlling the loop in another thread
    // Not declared as volatile -> updates may not become visible
    private boolean running = true;

    // Method expected to be called from another thread
    // Changes the flag to stop the loop
    public void stop() {
        running = false;
    }

    public void work() {
        // Thread continuously checks the flag
        // Without volatile/synchronization the JVM may reuse
        // a cached value and never observe the change
        while (running) {
            // Busy loop (intentional for demonstration)
        }
    }

    // Simple accessor exposing the current value
    // Also not synchronized -> may return stale value
    public boolean isRunning() {
        return running;
    }
}