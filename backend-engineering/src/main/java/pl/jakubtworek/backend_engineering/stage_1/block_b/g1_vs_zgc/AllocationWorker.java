package pl.jakubtworek.backend_engineering.stage_1.block_b.g1_vs_zgc;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Queue;

public final class AllocationWorker implements Runnable {

    private final WorkloadConfig config;
    private final LiveSet liveSet;
    private final Queue<List<Payload>> mediumLivedObjects = new ArrayDeque<>();

    private volatile boolean running = true;

    public AllocationWorker(WorkloadConfig config, LiveSet liveSet) {
        this.config = config;
        this.liveSet = liveSet;
    }

    @Override
    public void run() {
        long batches = 0;
        long checksum = 0;

        while (running) {
            // Ensure that the application has a meaningful live set.
            // This is important when comparing collectors on larger heaps.
            liveSet.growIfNeeded(config.objectSizeBytes());

            // This batch represents request-local allocations.
            // Most objects created here are short-lived.
            List<Payload> batch = new ArrayList<>(config.allocationBatchSize());

            for (int i = 0; i < config.allocationBatchSize(); i++) {
                Payload payload = new Payload(config.objectSizeBytes());
                checksum += payload.touch();
                batch.add(payload);
            }

            // Some objects are retained for a few cycles.
            // This simulates medium-lived objects that survive young collections.
            mediumLivedObjects.add(batch);

            while (mediumLivedObjects.size() > config.mediumLivedRetentionCycles()) {
                mediumLivedObjects.poll();
            }

            checksum += liveSet.touchSomeObjects();
            batches++;

            if (batches % 100 == 0) {
                System.out.println(
                        "batches=" + batches +
                        ", liveSetObjects=" + liveSet.size() +
                        ", mediumLivedBatches=" + mediumLivedObjects.size() +
                        ", checksum=" + checksum
                );
            }
        }
    }

    public void stop() {
        running = false;
    }
}