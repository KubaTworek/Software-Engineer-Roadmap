package pl.jakubtworek.backend_engineering.stage_1.block_b.cpu_vs_io;

public final class WaitBoundWorker implements Runnable {

    private final ProfilingConfig config;

    public WaitBoundWorker(ProfilingConfig config) {
        this.config = config;
    }

    @Override
    public void run() {
        // This worker simulates I/O-bound behavior.
        //
        // It spends most of its wall-clock time waiting.
        // It should NOT dominate CPU samples, but it should appear
        // in thread state analysis as sleeping / timed waiting.
        long operations = 0;

        while (!Thread.currentThread().isInterrupted()) {
            SimulatedIo.callExternalService(config.simulatedIoMillis());
            operations++;

            if (operations % 100 == 0) {
                System.out.println("wait-bound operations=" + operations);
            }
        }

        System.out.println("Wait-bound worker stopped, operations=" + operations);
    }
}