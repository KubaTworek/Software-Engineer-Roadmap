package pl.jakubtworek.lld.workflow.domain;

import java.util.Objects;
import java.util.Optional;

/**
 * Zwykła odmowa jest wynikiem oceny. Wyjątek powstaje dopiero przy wykonaniu.
 */
public record CommandAssessment(Optional<WorkflowException.Reason> refusal) {

    public CommandAssessment {
        Objects.requireNonNull(refusal);
    }

    public static CommandAssessment permit() {
        return new CommandAssessment(Optional.empty());
    }

    public static CommandAssessment deny(WorkflowException.Reason reason) {
        return new CommandAssessment(Optional.of(reason));
    }

    public boolean allowed() {
        return refusal.isEmpty();
    }
}
