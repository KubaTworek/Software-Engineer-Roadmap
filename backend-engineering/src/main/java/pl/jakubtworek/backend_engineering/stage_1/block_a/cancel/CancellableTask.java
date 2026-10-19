package pl.jakubtworek.backend_engineering.stage_1.block_a.cancel;

public class CancellableTask implements Runnable {

    /**
     * This task supports cooperative cancellation.
     * It periodically checks whether the thread has been interrupted.
     */
    @Override
    public void run() {
        try {
            while (!Thread.currentThread().isInterrupted()) {
                // A blocking method is also an interruption checkpoint.
                Thread.sleep(100);
            }
        } catch (InterruptedException e) {
            // InterruptedException clears the flag. Restore it before leaving so
            // callers higher in the stack do not lose the cancellation signal.
            Thread.currentThread().interrupt();
        }
    }
}
