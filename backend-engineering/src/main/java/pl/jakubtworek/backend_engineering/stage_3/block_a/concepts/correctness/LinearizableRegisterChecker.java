package pl.jakubtworek.backend_engineering.stage_3.block_a.concepts.correctness;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Exhaustive checker for a tiny single-register history. It is intentionally
 * bounded: production histories require specialized algorithms and tooling.
 */
public final class LinearizableRegisterChecker {

    private static final int MAX_OPERATIONS = 10;

    public boolean isLinearizable(int initialValue, List<RegisterCall> history) {
        List<RegisterCall> calls = List.copyOf(history);
        if (calls.size() > MAX_OPERATIONS) {
            throw new IllegalArgumentException("educational checker supports at most 10 operations");
        }
        if (calls.stream().map(RegisterCall::operationId).distinct().count() != calls.size()) {
            throw new IllegalArgumentException("operation ids must be unique");
        }
        return search(initialValue, calls, new HashSet<>());
    }

    private boolean search(int registerValue, List<RegisterCall> calls, Set<String> placed) {
        if (placed.size() == calls.size()) {
            return true;
        }

        for (RegisterCall candidate : calls) {
            if (placed.contains(candidate.operationId()) || !allRealTimePredecessorsPlaced(candidate, calls, placed)) {
                continue;
            }

            Integer nextValue = apply(registerValue, candidate);
            if (nextValue == null) {
                continue;
            }
            placed.add(candidate.operationId());
            if (search(nextValue, calls, placed)) {
                return true;
            }
            placed.remove(candidate.operationId());
        }
        return false;
    }

    private static boolean allRealTimePredecessorsPlaced(
            RegisterCall candidate,
            List<RegisterCall> calls,
            Set<String> placed) {
        return calls.stream()
                .filter(other -> other.completedAt() < candidate.invokedAt())
                .allMatch(other -> placed.contains(other.operationId()));
    }

    private static Integer apply(int registerValue, RegisterCall call) {
        if (call.type() == RegisterCall.Type.WRITE) {
            return call.result() == call.argument() ? call.argument() : null;
        }
        return call.result() == registerValue ? registerValue : null;
    }
}
