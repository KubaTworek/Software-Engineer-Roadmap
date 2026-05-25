package pl.jakubtworek.backend_engineering.stage_3.block_b.alerts.runbook;

/**
 * Represents a single actionable runbook step.
 *
 * A good step should be specific enough that a responder can execute it under pressure.
 */
public record RunbookStep(
        int number,
        String instruction
) {

    public RunbookStep {
        if (number < 1) {
            throw new IllegalArgumentException("step number must be positive");
        }

        if (instruction == null || instruction.isBlank()) {
            throw new IllegalArgumentException("instruction must not be blank");
        }
    }
}