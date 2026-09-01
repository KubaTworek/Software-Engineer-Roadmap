package pl.jakubtworek.backend_engineering.stage_1.block_a.virtual_threads;

@FunctionalInterface
public interface InterruptibleOperation<T> {
    T execute() throws InterruptedException;
}
