package pl.jakubtworek.backend_engineering.stage_1.block_b.thread_count;

public final class WaitBoundWorkload implements Workload {

    private final int waitMillis;

    public WaitBoundWorkload(int waitMillis) {
        this.waitMillis = waitMillis;
    }

    @Override
    public long runOneOperation() {
        // This workload simulates blocking I/O or waiting.
        //
        // A thread spends most of its wall-clock time not using CPU.
        // More threads may improve throughput until another bottleneck appears.
        try {
            Thread.sleep(waitMillis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        return 1;
    }
}