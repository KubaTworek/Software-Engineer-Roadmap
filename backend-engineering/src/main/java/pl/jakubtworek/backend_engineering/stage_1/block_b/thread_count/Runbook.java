package pl.jakubtworek.backend_engineering.stage_1.block_b.thread_count;

public final class Runbook {

    public static void main(String[] args) {
        // Compile:
        //
        // javac *.java
        //
        // CPU-bound examples:
        //
        // java ThreadCountTuningExperiment --mode=cpu --threads=1
        // java ThreadCountTuningExperiment --mode=cpu --threads=2
        // java ThreadCountTuningExperiment --mode=cpu --threads=4
        // java ThreadCountTuningExperiment --mode=cpu --threads=8
        // java ThreadCountTuningExperiment --mode=cpu --threads=16
        //
        // Wait-bound examples:
        //
        // java ThreadCountTuningExperiment --mode=wait --threads=1
        // java ThreadCountTuningExperiment --mode=wait --threads=8
        // java ThreadCountTuningExperiment --mode=wait --threads=32
        // java ThreadCountTuningExperiment --mode=wait --threads=128
        //
        // Mixed workload examples:
        //
        // java ThreadCountTuningExperiment --mode=mixed --threads=4
        // java ThreadCountTuningExperiment --mode=mixed --threads=16
        // java ThreadCountTuningExperiment --mode=mixed --threads=64
        //
        // Observe:
        // - throughput growth,
        // - point of saturation,
        // - throughput collapse with too many threads,
        // - CPU usage,
        // - context switching,
        // - thread states in JFR or OS tools.

        System.out.println("See source comments for experiment commands.");
    }
}