package pl.jakubtworek.backend_engineering.stage_1.block_a.cancel;

public class CancellableTask implements Runnable {

    /**
     * This task supports cooperative cancellation.
     * It periodically checks whether the thread has been interrupted.
     */
    @Override
    public void run() {

        try {
            // loop runs until an interrupt signal is detected
            while (!Thread.currentThread().isInterrupted()) {

                // simulate blocking work
                Thread.sleep(100);

                // additional computation could happen here
            }

        } catch (InterruptedException e) {

            // sleep() throws InterruptedException when the thread is interrupted
            // and clears the interrupt flag
            // restoring the flag preserves the interruption information
            Thread.currentThread().interrupt();
        }

        // task exits cleanly after interruption
        System.out.println("Task stopped cooperatively.");
    }
}