package pl.jakubtworek.backend_systems_lab_stage_1.block_b.lock_contention;

public final class SynchronizedCounter implements Counter {

    private long value;

    @Override
    public synchronized void increment() {
        // This method uses the object's monitor.
        //
        // Under contention, only one thread can enter this method at a time.
        // Other threads may become blocked or parked while waiting for the monitor.
        value++;
    }

    @Override
    public synchronized long value() {
        // This read is also synchronized to preserve visibility and consistency.
        return value;
    }
}