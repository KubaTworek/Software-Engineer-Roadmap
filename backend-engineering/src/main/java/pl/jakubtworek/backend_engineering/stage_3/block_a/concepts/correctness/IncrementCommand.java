package pl.jakubtworek.backend_engineering.stage_3.block_a.concepts.correctness;

import java.util.Objects;

public record IncrementCommand(String commandId, long amount) {

    public IncrementCommand {
        Objects.requireNonNull(commandId, "commandId must not be null");
        if (commandId.isBlank()) {
            throw new IllegalArgumentException("commandId must not be blank");
        }
        if (amount <= 0) {
            throw new IllegalArgumentException("amount must be positive");
        }
    }
}
