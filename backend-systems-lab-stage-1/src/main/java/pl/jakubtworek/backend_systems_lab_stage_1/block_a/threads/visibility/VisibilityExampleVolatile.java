package pl.jakubtworek.backend_systems_lab_stage_1.block_a.threads.visibility;

/**
 * Version that fixes the visibility issue using a volatile flag.
 *
 * The field `running` is shared between threads:
 *  - one thread executes work()
 *  - another thread calls stop()
 *
 * Declaring the field as volatile guarantees that updates made
 * in stop() become visible to the thread executing the loop.
 */
public class VisibilityExampleVolatile {

    // Volatile ensures that writes to this flag
    // are immediately visible to other threads
    private volatile boolean running = true;

    // Called from another thread to stop the worker
    // The write to a volatile variable becomes visible
    // to subsequent reads in other threads
    public void stop() {
        running = false;
    }

    public void work() {
        // The loop reads the volatile variable each iteration,
        // so the thread cannot cache its value
        while (running) {
            // Busy loop used only to demonstrate the visibility example
        }
    }

    // Simple accessor returning the current value
    // Volatile guarantees the caller sees the latest write
    public boolean isRunning() {
        return running;
    }
}