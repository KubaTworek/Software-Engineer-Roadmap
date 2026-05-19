package pl.jakubtworek.backend_systems_lab_stage_1.block_b.thread_count;

public final class ThreadCountTuningExperiment {

    public static void main(String[] args) throws Exception {
        // This experiment shows that the "right" number of threads
        // depends on the type of workload:
        //
        // - CPU-bound work usually scales up to the number of available cores.
        // - Wait-bound work may benefit from more threads because many threads are waiting.
        // - Too many threads can hurt performance due to scheduling and context switching.
        //
        // Example:
        // java ThreadCountTuningExperiment --mode=cpu --threads=8
        // java ThreadCountTuningExperiment --mode=wait --threads=64

        ExperimentConfig config = ExperimentConfig.fromArgs(args);

        System.out.println("Starting thread count tuning experiment");
        System.out.println(config);
        System.out.println("Available processors: " + Runtime.getRuntime().availableProcessors());

        Workload workload = switch (config.mode()) {
            case "cpu" -> new CpuBoundWorkload(config.cpuIterations());
            case "wait" -> new WaitBoundWorkload(config.waitMillis());
            case "mixed" -> new MixedWorkload(config.cpuIterations(), config.waitMillis());
            default -> throw new IllegalArgumentException("Unknown mode: " + config.mode());
        };

        ExperimentRunner runner = new ExperimentRunner(config, workload);
        runner.run();

        System.out.println("Experiment finished");
    }
}