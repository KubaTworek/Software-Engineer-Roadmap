package pl.jakubtworek.backend_engineering.stage_1.block_b.lock_contention;

import java.util.concurrent.atomic.AtomicLong;

public final class AtomicCounter implements Counter {

    private final AtomicLong value = new AtomicLong();

    @Override
    public void increment() {
        // AtomicLong uses CAS-based updates.
        //
        // It avoids monitor blocking, but all threads still compete
        // for the same memory location.
        //
        // Under heavy contention, failed CAS retries can waste CPU cycles.
        value.incrementAndGet();
    }

    @Override
    public long value() {
        return value.get();
    }
}