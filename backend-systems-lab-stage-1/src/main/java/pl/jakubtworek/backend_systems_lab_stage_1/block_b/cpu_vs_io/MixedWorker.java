package pl.jakubtworek.backend_systems_lab_stage_1.block_b.cpu_vs_io;

public final class MixedWorker implements Runnable {

    private final ProfilingConfig config;

    public MixedWorker(ProfilingConfig config) {
        this.config = config;
    }

    @Override
    public void run() {
        // This worker represents a realistic mixed workload:
        // some CPU work followed by a wait.
        //
        // In JFR this helps compare:
        // - CPU samples,
        // - wall-clock time,
        // - thread state distribution.
        long checksum = 0;
        long operations = 0;

        while (!Thread.currentThread().isInterrupted()) {
            checksum += PrimeCalculator.countPrimes(config.mixedCpuIterations());
            SimulatedIo.callExternalService(config.mixedWaitMillis());
            operations++;

            if (operations % 100 == 0) {
                System.out.println("mixed operations=" + operations + ", checksum=" + checksum);
            }
        }

        System.out.println("Mixed worker stopped, operations=" + operations + ", checksum=" + checksum);
    }
}