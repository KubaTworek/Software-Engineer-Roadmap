package pl.jakubtworek.backend_engineering.stage_1.block_b.lock_contention;

public final class CounterWorker implements Runnable {

    private final Counter counter;
    private final long endAtNanos;

    private long operations;

    public CounterWorker(Counter counter, long endAtNanos) {
        this.counter = counter;
        this.endAtNanos = endAtNanos;
    }

    @Override
    public void run() {
        // Each worker repeatedly increments the same shared counter.
        //
        // This intentionally creates contention:
        // - monitor contention for synchronized,
        // - CAS contention for AtomicLong,
        // - reduced contention for LongAdder.
        while (System.nanoTime() < endAtNanos) {
            counter.increment();
            operations++;
        }
    }

    public long operations() {
        return operations;
    }
}