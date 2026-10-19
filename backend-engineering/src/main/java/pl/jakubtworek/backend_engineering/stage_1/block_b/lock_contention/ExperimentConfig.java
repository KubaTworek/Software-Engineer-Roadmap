package pl.jakubtworek.backend_engineering.stage_1.block_b.lock_contention;

public record ExperimentConfig(
        int threadCount,
        int durationSecondsPerScenario
) {

    public ExperimentConfig {
        if (threadCount <= 0) {
            throw new IllegalArgumentException("threadCount must be greater than zero");
        }
        if (durationSecondsPerScenario <= 0) {
            throw new IllegalArgumentException("durationSecondsPerScenario must be greater than zero");
        }
    }

    public static ExperimentConfig fromArgs(String[] args) {
        // Default thread count uses available processors.
        // This usually creates enough contention to make differences visible.
        int threadCount = Runtime.getRuntime().availableProcessors();
        int durationSecondsPerScenario = 20;

        // Example:
        // --threads=16 --durationSecondsPerScenario=30
        for (String arg : args) {
            if (arg.startsWith("--threads=")) {
                threadCount = Integer.parseInt(arg.substring("--threads=".length()));
            } else if (arg.startsWith("--durationSecondsPerScenario=")) {
                durationSecondsPerScenario = Integer.parseInt(
                        arg.substring("--durationSecondsPerScenario=".length())
                );
            } else {
                throw new IllegalArgumentException("Unsupported argument: " + arg);
            }
        }

        return new ExperimentConfig(threadCount, durationSecondsPerScenario);
    }
}
