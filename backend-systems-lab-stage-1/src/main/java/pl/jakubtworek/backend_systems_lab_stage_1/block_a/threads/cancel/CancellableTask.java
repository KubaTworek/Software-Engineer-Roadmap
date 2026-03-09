package pl.jakubtworek.backend_systems_lab_stage_1.block_a.threads.cancel;

public class CancellableTask implements Runnable {

    /**
     * Cooperative cancellation.
     *
     * Task periodically checks interruption flag.
     * If interrupted during sleep, it restores interrupt flag
     * and exits gracefully.
     */
    @Override
    public void run() {

        try {
            while (!Thread.currentThread().isInterrupted()) {

                // simulate long work
                Thread.sleep(100);

                // could also do computation here
            }

        } catch (InterruptedException e) {

            // Sleep throws InterruptedException and CLEARS the flag.
            // We restore it to preserve interruption semantics.
            Thread.currentThread().interrupt();
        }

        System.out.println("Task stopped cooperatively.");
    }
}