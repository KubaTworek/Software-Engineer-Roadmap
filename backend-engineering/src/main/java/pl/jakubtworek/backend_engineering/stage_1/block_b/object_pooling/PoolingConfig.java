package pl.jakubtworek.backend_engineering.stage_1.block_b.object_pooling;

public record PoolingConfig(
        int iterations,
        int payloadSizeBytes,
        int poolSize,
        int workerThreads,
        int pauseBetweenScenariosMillis
) {

    public static PoolingConfig fromArgs(String[] args) {
        // Defaults are intentionally moderate.
        // They should make allocation and pooling behavior visible without requiring a huge heap.
        int iterations = 5_000_000;
        int payloadSizeBytes = 256;
        int poolSize = 10_000;
        int workerThreads = Runtime.getRuntime().availableProcessors();
        int pauseBetweenScenariosMillis = 3_000;

        // Example:
        // --iterations=10000000 --payloadSizeBytes=512 --poolSize=50000 --workerThreads=16
        for (String arg : args) {
            if (arg.startsWith("--iterations=")) {
                iterations = Integer.parseInt(arg.substring("--iterations=".length()));
            } else if (arg.startsWith("--payloadSizeBytes=")) {
                payloadSizeBytes = Integer.parseInt(arg.substring("--payloadSizeBytes=".length()));
            } else if (arg.startsWith("--poolSize=")) {
                poolSize = Integer.parseInt(arg.substring("--poolSize=".length()));
            } else if (arg.startsWith("--workerThreads=")) {
                workerThreads = Integer.parseInt(arg.substring("--workerThreads=".length()));
            } else if (arg.startsWith("--pauseBetweenScenariosMillis=")) {
                pauseBetweenScenariosMillis = Integer.parseInt(
                        arg.substring("--pauseBetweenScenariosMillis=".length())
                );
            }
        }

        return new PoolingConfig(
                iterations,
                payloadSizeBytes,
                poolSize,
                workerThreads,
                pauseBetweenScenariosMillis
        );
    }
}