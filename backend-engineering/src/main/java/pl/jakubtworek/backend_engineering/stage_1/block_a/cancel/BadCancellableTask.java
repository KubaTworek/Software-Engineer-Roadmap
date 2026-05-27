package pl.jakubtworek.backend_engineering.stage_1.block_a.cancel;

/**
 * Problem in this example:
 * - the task does not respond to thread interruption
 * - InterruptedException is caught but ignored
 * - Thread.sleep() clears the interrupt flag when throwing the exception
 *
 * Result:
 * - the loop continues running indefinitely
 * - the task cannot be properly cancelled using Thread.interrupt()
 */
public class BadCancellableTask implements Runnable {

    @Override
    public void run() {

        // infinite loop with no interrupt state check
        while (true) {

            try {
                Thread.sleep(100);

            } catch (InterruptedException ignored) {

                // ❌ BAD: interrupt signal is ignored
                // the interrupt flag has been cleared by sleep()
                // the thread keeps running instead of stopping
            }
        }
    }
}