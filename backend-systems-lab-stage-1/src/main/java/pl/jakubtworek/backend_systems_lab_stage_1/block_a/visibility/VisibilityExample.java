package pl.jakubtworek.backend_systems_lab_stage_1.block_a.visibility;

/**
 * Demonstrates visibility problem under Java Memory Model.
 *
 * running is not volatile and not synchronized.
 *
 * Another thread may:
 *  - cache the value in register
 *  - never observe the update
 *  - spin forever
 */
public class VisibilityExample {

    private boolean running = true;

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