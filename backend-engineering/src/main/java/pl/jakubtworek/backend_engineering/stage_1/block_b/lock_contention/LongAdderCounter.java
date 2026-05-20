package pl.jakubtworek.backend_engineering.stage_1.block_b.lock_contention;

import java.util.concurrent.atomic.LongAdder;

public final class LongAdderCounter implements Counter {

    private final LongAdder value = new LongAdder();

    @Override
    public void increment() {
        // LongAdder spreads updates across multiple internal cells.
        //
        // This reduces contention on a single memory location.
        // It is designed for high-update, low-read-frequency counters.
        value.increment();
    }

    @Override
    public long value() {
        // sum() aggregates all internal cells.
        // It is more expensive than AtomicLong.get(),
        // but usually acceptable when reads are infrequent.
        return value.sum();
    }
}