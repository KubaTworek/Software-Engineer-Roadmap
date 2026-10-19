package pl.jakubtworek.cloudarchitecture.operations.recovery;

import java.util.List;

public record RollbackDecision(boolean safe, List<String> actions, String rationale) {

    public RollbackDecision {
        actions = List.copyOf(actions);
    }
}
