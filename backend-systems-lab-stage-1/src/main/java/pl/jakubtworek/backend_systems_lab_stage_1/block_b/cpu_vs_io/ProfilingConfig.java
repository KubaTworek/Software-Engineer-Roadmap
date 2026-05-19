package pl.jakubtworek.backend_systems_lab_stage_1.block_b.cpu_vs_io;

public record ProfilingConfig(
        int durationSeconds,
        int cpuIterations,
        int simulatedIoMillis,
        int mixedCpuIterations,
        int mixedWaitMillis
) {

    public static ProfilingConfig fromArgs(String[] args) {
        // Defaults are chosen to make both CPU-bound and wait-bound behavior visible in JFR.
        int durationSeconds = 60;
        int cpuIterations = 50_000;
        int simulatedIoMillis = 50;
        int mixedCpuIterations = 10_000;
        int mixedWaitMillis = 20;

        // Example:
        // --durationSeconds=120 --simulatedIoMillis=100
        for (String arg : args) {
            if (arg.startsWith("--durationSeconds=")) {
                durationSeconds = Integer.parseInt(arg.substring("--durationSeconds=".length()));
            } else if (arg.startsWith("--cpuIterations=")) {
                cpuIterations = Integer.parseInt(arg.substring("--cpuIterations=".length()));
            } else if (arg.startsWith("--simulatedIoMillis=")) {
                simulatedIoMillis = Integer.parseInt(arg.substring("--simulatedIoMillis=".length()));
            } else if (arg.startsWith("--mixedCpuIterations=")) {
                mixedCpuIterations = Integer.parseInt(arg.substring("--mixedCpuIterations=".length()));
            } else if (arg.startsWith("--mixedWaitMillis=")) {
                mixedWaitMillis = Integer.parseInt(arg.substring("--mixedWaitMillis=".length()));
            }
        }

        return new ProfilingConfig(
                durationSeconds,
                cpuIterations,
                simulatedIoMillis,
                mixedCpuIterations,
                mixedWaitMillis
        );
    }
}