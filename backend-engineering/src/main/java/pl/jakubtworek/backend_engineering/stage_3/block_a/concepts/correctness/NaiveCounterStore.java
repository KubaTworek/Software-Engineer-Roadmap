package pl.jakubtworek.backend_engineering.stage_3.block_a.concepts.correctness;

/** Applies every physical attempt, including retries of the same logical command. */
public final class NaiveCounterStore implements CounterStore {

    private long value;

    @Override
    public synchronized ApplyResult apply(IncrementCommand command) {
        value = Math.addExact(value, command.amount());
        return new ApplyResult(true, value);
    }

    @Override
    public synchronized long value() {
        return value;
    }
}
