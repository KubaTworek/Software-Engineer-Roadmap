package pl.jakubtworek.backend_engineering.stage_1.block_b.cpu_vs_io;

public final class SimulatedIo {

    private SimulatedIo() {
    }

    public static void callExternalService(int latencyMillis) {
        // This method simulates a blocking I/O call.
        //
        // It consumes wall-clock time but almost no CPU.
        // In JFR it should show up as timed waiting / sleeping,
        // not as a CPU hotspot.
        try {
            Thread.sleep(latencyMillis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}