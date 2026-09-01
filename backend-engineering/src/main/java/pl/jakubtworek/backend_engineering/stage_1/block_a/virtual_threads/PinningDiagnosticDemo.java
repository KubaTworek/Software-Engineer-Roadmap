package pl.jakubtworek.backend_engineering.stage_1.block_a.virtual_threads;

/** Run with -Djdk.tracePinnedThreads=full on Java 21 to print the pinning stack. */
public final class PinningDiagnosticDemo {

    private PinningDiagnosticDemo() {
    }

    public static void main(String[] args) throws InterruptedException {
        PinningExamples examples = new PinningExamples();
        Thread virtualThread = Thread.ofVirtual().name("pinned-demo").start(() -> {
            try {
                examples.blockingWhileHoldingMonitor(() -> {
                    Thread.sleep(100);
                    return null;
                });
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
            }
        });
        virtualThread.join();
    }
}
