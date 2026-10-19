package pl.jakubtworek.backend_engineering.stage_3.block_a.concepts.correctness;

public interface CounterStore {

    ApplyResult apply(IncrementCommand command);

    long value();

    record ApplyResult(boolean applied, long valueAfter) {
    }
}
