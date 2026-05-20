package pl.jakubtworek.backend_engineering.stage_1.block_b.thread_count;

public final class Worker implements Runnable {

    private final Workload workload;
    private final long endAtNanos;

    private long operations;
    private long checksum;

    public Worker(Workload workload, long endAtNanos) {
        this.workload = workload;
        this.endAtNanos = endAtNanos;
    }

    @Override
    public void run() {
        // Each worker executes the same workload until the experiment time ends.
        //
        // Comparing total throughput for different thread counts shows
        // where scaling stops and where overhead starts to dominate.
        while (System.nanoTime() < endAtNanos && !Thread.currentThread().isInterrupted()) {
            checksum += workload.runOneOperation();
            operations++;
        }
    }

    public long operations() {
        return operations;
    }

    public long checksum() {
        return checksum;
    }
}