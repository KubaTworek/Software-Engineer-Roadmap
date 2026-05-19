package pl.jakubtworek.backend_systems_lab_stage_1.block_b.thread_count;

public record ExperimentConfig(
        String mode,
        int threadCount,
        int durationSeconds,
        int cpuIterations,
        int waitMillis
) {

    public static ExperimentConfig fromArgs(String[] args) {
        // Defaults are intentionally simple.
        // They allow quick experiments without extra configuration.
        String mode = "cpu";
        int threadCount = Runtime.getRuntime().availableProcessors();
        int durationSeconds = 20;
        int cpuIterations = 50_000;
        int waitMillis = 20;

        // Supported arguments:
        // --mode=cpu
        // --mode=wait
        // --mode=mixed
        // --threads=16
        // --durationSeconds=30
        // --cpuIterations=100000
        // --waitMillis=50
        for (String arg : args) {
            if (arg.startsWith("--mode=")) {
                mode = arg.substring("--mode=".length());
            } else if (arg.startsWith("--threads=")) {
                threadCount = Integer.parseInt(arg.substring("--threads=".length()));
            } else if (arg.startsWith("--durationSeconds=")) {
                durationSeconds = Integer.parseInt(arg.substring("--durationSeconds=".length()));
            } else if (arg.startsWith("--cpuIterations=")) {
                cpuIterations = Integer.parseInt(arg.substring("--cpuIterations=".length()));
            } else if (arg.startsWith("--waitMillis=")) {
                waitMillis = Integer.parseInt(arg.substring("--waitMillis=".length()));
            }
        }

        return new ExperimentConfig(
                mode,
                threadCount,
                durationSeconds,
                cpuIterations,
                waitMillis
        );
    }
}