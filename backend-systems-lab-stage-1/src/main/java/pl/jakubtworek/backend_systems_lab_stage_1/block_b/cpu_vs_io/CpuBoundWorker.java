package pl.jakubtworek.backend_systems_lab_stage_1.block_b.cpu_vs_io;

public final class CpuBoundWorker implements Runnable {

    private final ProfilingConfig config;

    public CpuBoundWorker(ProfilingConfig config) {
        this.config = config;
    }

    @Override
    public void run() {
        // This worker continuously performs CPU-heavy calculations.
        //
        // In JFR / JMC, this should appear in:
        // - Hot Methods,
        // - Method Profiling,
        // - CPU samples,
        // - runnable thread state.
        long checksum = 0;

        while (!Thread.currentThread().isInterrupted()) {
            checksum += PrimeCalculator.countPrimes(config.cpuIterations());

            // Print occasionally so the result remains observable
            // without dominating the profile with console I/O.
            if ((checksum & 0xFFFF) == 0) {
                System.out.println("cpu checksum=" + checksum);
            }
        }

        System.out.println("CPU worker stopped, checksum=" + checksum);
    }
}