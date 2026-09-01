package pl.jakubtworek.backend_engineering.stage_3.block_a.concepts.correctness;

import java.util.HashSet;
import java.util.Set;

/** Atomically records the command marker and its effect. */
public final class IdempotentCounterStore implements CounterStore {

    private final Set<String> processedCommandIds = new HashSet<>();
    private long value;

    @Override
    public synchronized ApplyResult apply(IncrementCommand command) {
        if (!processedCommandIds.add(command.commandId())) {
            return new ApplyResult(false, value);
        }
        value = Math.addExact(value, command.amount());
        return new ApplyResult(true, value);
    }

    @Override
    public synchronized long value() {
        return value;
    }
}
