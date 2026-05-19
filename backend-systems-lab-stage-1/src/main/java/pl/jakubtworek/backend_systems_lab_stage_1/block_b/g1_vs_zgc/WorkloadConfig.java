package pl.jakubtworek.backend_systems_lab_stage_1.block_b.g1_vs_zgc;

public record WorkloadConfig(
        int durationSeconds,
        int allocationBatchSize,
        int objectSizeBytes,
        int liveSetTargetMb,
        int mediumLivedRetentionCycles,
        int latencyProbeSleepMillis
) {

    public static WorkloadConfig fromArgs(String[] args) {
        // Defaults are intentionally moderate.
        // They should create visible GC activity without requiring a very large machine.
        int durationSeconds = 120;
        int allocationBatchSize = 20_000;
        int objectSizeBytes = 512;
        int liveSetTargetMb = 512;
        int mediumLivedRetentionCycles = 20;
        int latencyProbeSleepMillis = 10;

        // A tiny argument parser is enough for this demo.
        // Example:
        // --durationSeconds=180 --liveSetTargetMb=2048
        for (String arg : args) {
            if (arg.startsWith("--durationSeconds=")) {
                durationSeconds = Integer.parseInt(arg.substring("--durationSeconds=".length()));
            } else if (arg.startsWith("--allocationBatchSize=")) {
                allocationBatchSize = Integer.parseInt(arg.substring("--allocationBatchSize=".length()));
            } else if (arg.startsWith("--objectSizeBytes=")) {
                objectSizeBytes = Integer.parseInt(arg.substring("--objectSizeBytes=".length()));
            } else if (arg.startsWith("--liveSetTargetMb=")) {
                liveSetTargetMb = Integer.parseInt(arg.substring("--liveSetTargetMb=".length()));
            } else if (arg.startsWith("--mediumLivedRetentionCycles=")) {
                mediumLivedRetentionCycles = Integer.parseInt(arg.substring("--mediumLivedRetentionCycles=".length()));
            } else if (arg.startsWith("--latencyProbeSleepMillis=")) {
                latencyProbeSleepMillis = Integer.parseInt(arg.substring("--latencyProbeSleepMillis=".length()));
            }
        }

        return new WorkloadConfig(
                durationSeconds,
                allocationBatchSize,
                objectSizeBytes,
                liveSetTargetMb,
                mediumLivedRetentionCycles,
                latencyProbeSleepMillis
        );
    }
}