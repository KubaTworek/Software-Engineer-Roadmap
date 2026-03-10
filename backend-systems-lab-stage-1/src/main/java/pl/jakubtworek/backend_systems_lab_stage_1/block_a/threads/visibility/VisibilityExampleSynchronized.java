package pl.jakubtworek.backend_systems_lab_stage_1.block_a.threads.visibility;

/**
 * Version that fixes the visibility issue using synchronized methods.
 *
 * Both reading and writing the shared flag happen under the same monitor.
 * Because of this, a write in stop() becomes visible to threads calling
 * isRunning().
 */
public class VisibilityExampleSynchronized {

    // Shared flag controlling the worker loop
    private boolean running = true;

    // Called from another thread to stop the worker
    // synchronized ensures the write is visible to threads
    // that later acquire the same monitor
    public synchronized void stop() {
        running = false;
    }

    // Synchronized read of the shared flag
    // guarantees the thread observes the latest value
    public synchronized boolean isRunning() {
        return running;
    }

    public void work() {
        // Each iteration calls synchronized isRunning(),
        // so the thread repeatedly acquires the monitor
        // and re-checks the current value of running
        while (isRunning()) {
            // Busy loop used only to demonstrate the visibility issue
        }
    }
}