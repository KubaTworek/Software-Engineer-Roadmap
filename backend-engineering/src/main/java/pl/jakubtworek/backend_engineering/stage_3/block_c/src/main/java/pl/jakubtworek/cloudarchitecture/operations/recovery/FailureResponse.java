package pl.jakubtworek.cloudarchitecture.operations.recovery;

import java.util.List;

public record FailureResponse(
        CloudDependency failedDependency,
        ContinuityMode mode,
        boolean durableDataAtRisk,
        List<String> actions,
        String rationale) {

    public FailureResponse {
        actions = List.copyOf(actions);
    }
}
