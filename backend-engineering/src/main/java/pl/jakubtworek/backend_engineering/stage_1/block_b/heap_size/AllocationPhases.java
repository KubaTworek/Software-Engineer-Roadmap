package pl.jakubtworek.backend_engineering.stage_1.block_b.heap_size;

import java.util.ArrayList;
import java.util.List;

public final class AllocationPhases {

    private static final int MB = 1024 * 1024;

    public void warmupPhase() throws InterruptedException {
        // Warmup creates moderate allocation pressure.
        // This allows the JVM to initialize compilation and baseline heap usage.
        System.out.println("Warmup phase started");

        runAllocationBurst(
                50,
                2 * MB,
                50
        );

        System.out.println("Warmup phase finished");
    }

    public void spikePhase() throws InterruptedException {
        // This phase creates a temporary memory spike.
        //
        // If -Xms is much smaller than -Xmx,
        // the JVM may need to repeatedly expand the heap.
        //
        // Heap expansion itself has runtime cost and can affect pause predictability.
        System.out.println("Spike phase started");

        runAllocationBurst(
                200,
                4 * MB,
                10
        );

        System.out.println("Spike phase finished");
    }

    public void cooldownPhase() throws InterruptedException {
        // This phase intentionally reduces allocation activity.
        //
        // Some collectors may decide to shrink parts of the heap over time.
        // Later re-expansion may introduce additional runtime overhead.
        System.out.println("Cooldown phase started");

        Thread.sleep(10_000);

        System.out.println("Cooldown phase finished");
    }

    public void stableLoadPhase() throws InterruptedException {
        // Stable load represents a long-running service under moderate traffic.
        //
        // This phase helps observe whether the heap has stabilized
        // or whether resizing activity continues.
        System.out.println("Stable load phase started");

        runAllocationBurst(
                500,
                512 * 1024,
                20
        );

        System.out.println("Stable load phase finished");
    }

    private void runAllocationBurst(
            int iterations,
            int objectSizeBytes,
            int sleepMillis
    ) throws InterruptedException {

        for (int i = 0; i < iterations; i++) {

            // Temporary objects are intentionally short-lived.
            // This generates allocation pressure and GC activity.
            List<byte[]> temporaryObjects = new ArrayList<>();

            for (int j = 0; j < 100; j++) {
                temporaryObjects.add(new byte[objectSizeBytes]);
            }

            // Touch memory so allocations become observable and committed.
            long checksum = 0;

            for (byte[] array : temporaryObjects) {
                array[0] = 1;
                checksum += array[0];
            }

            // Printing periodically helps correlate console output with GC logs.
            if (i % 25 == 0) {
                System.out.println(
                        "iteration=" + i +
                        ", objectSizeBytes=" + objectSizeBytes +
                        ", checksum=" + checksum
                );
            }

            Thread.sleep(sleepMillis);
        }
    }
}