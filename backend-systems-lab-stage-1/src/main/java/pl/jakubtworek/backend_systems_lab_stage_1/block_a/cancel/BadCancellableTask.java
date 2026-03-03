package pl.jakubtworek.backend_systems_lab_stage_1.block_a.cancel;

/**
 * Anti-pattern:
 * InterruptedException is swallowed.
 *
 * This task ignores cancellation signal.
 */
public class BadCancellableTask implements Runnable {

    @Override
    public void run() {

        while (true) {

            try {
                Thread.sleep(100);
            } catch (InterruptedException ignored) {
                // ❌ BAD: ignoring interrupt
                // flag cleared, loop continues forever
            }
        }
    }
}