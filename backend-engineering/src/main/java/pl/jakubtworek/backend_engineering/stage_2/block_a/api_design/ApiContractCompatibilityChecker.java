package pl.jakubtworek.backend_engineering.stage_2.block_a.api_design;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Mały model review kontraktu. Nie zastępuje parsera OpenAPI, lecz pokazuje,
 * które zmiany muszą zatrzymać pipeline przed wdrożeniem.
 */
public final class ApiContractCompatibilityChecker {

    public Result compare(ApiContract previous, ApiContract candidate) {
        Map<OperationKey, Operation> candidateOperations = index(candidate.operations());
        List<String> breaking = new ArrayList<>();

        for (Operation oldOperation : previous.operations()) {
            Operation next = candidateOperations.get(oldOperation.key());
            if (next == null) {
                breaking.add("removed operation " + oldOperation.key());
                continue;
            }

            Set<String> newlyRequired = new HashSet<>(next.requiredRequestFields());
            newlyRequired.removeAll(oldOperation.requiredRequestFields());
            newlyRequired.forEach(field -> breaking.add("new required request field " + field + " in " + oldOperation.key()));

            Set<Integer> removedStatuses = new HashSet<>(oldOperation.successStatuses());
            removedStatuses.removeAll(next.successStatuses());
            removedStatuses.forEach(status -> breaking.add("removed success response " + status + " from " + oldOperation.key()));

            Set<String> removedResponseFields = new HashSet<>(oldOperation.responseFields());
            removedResponseFields.removeAll(next.responseFields());
            removedResponseFields.forEach(field -> breaking.add("removed response field " + field + " from " + oldOperation.key()));
        }
        return new Result(breaking.isEmpty(), List.copyOf(breaking));
    }

    private static Map<OperationKey, Operation> index(List<Operation> operations) {
        Map<OperationKey, Operation> indexed = new HashMap<>();
        for (Operation operation : operations) {
            if (indexed.put(operation.key(), operation) != null) {
                throw new IllegalArgumentException("duplicate operation " + operation.key());
            }
        }
        return indexed;
    }

    public record ApiContract(List<Operation> operations) {
        public ApiContract {
            operations = List.copyOf(operations);
        }
    }

    public record Operation(
            OperationKey key,
            Set<String> requiredRequestFields,
            Set<String> responseFields,
            Set<Integer> successStatuses
    ) {
        public Operation {
            requiredRequestFields = Set.copyOf(requiredRequestFields);
            responseFields = Set.copyOf(responseFields);
            successStatuses = Set.copyOf(successStatuses);
        }
    }

    public record OperationKey(String method, String path) {
        public OperationKey {
            method = method.toUpperCase();
        }

        @Override
        public String toString() {
            return method + " " + path;
        }
    }

    public record Result(boolean compatible, List<String> breakingChanges) {
    }
}
