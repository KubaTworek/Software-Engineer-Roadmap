package pl.jakubtworek.backend_engineering.stage_1.block_b.lock_contention;

public interface Counter {

    // This method is intentionally tiny.
    // The experiment focuses on synchronization and contention overhead.
    void increment();

    // Reading the final value makes the counter observable.
    long value();
}