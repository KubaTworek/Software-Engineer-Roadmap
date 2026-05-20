package pl.jakubtworek.backend_engineering.stage_1.block_b.heap_size;

public final class MemoryPressureMonitor implements Runnable {

    private volatile boolean running = true;

    @Override
    public void run() {

        // This monitor periodically prints JVM heap statistics.
        //
        // It helps correlate:
        // - used heap,
        // - committed heap,
        // - max heap,
        // with GC logs and allocation phases.
        while (running) {

            Runtime runtime = Runtime.getRuntime();

            long used = runtime.totalMemory() - runtime.freeMemory();
            long committed = runtime.totalMemory();
            long max = runtime.maxMemory();

            System.out.println(
                    "[MEMORY] " +
                    "usedMB=" + toMb(used) +
                    ", committedMB=" + toMb(committed) +
                    ", maxMB=" + toMb(max)
            );

            try {
                Thread.sleep(2000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
        }
    }

    public void stop() {
        running = false;
    }

    private long toMb(long bytes) {
        return bytes / 1024 / 1024;
    }
}