package pl.jakubtworek.lld.workflow.domain;

import java.util.Map;

/**
 * Dostępność przygotowania operacji dla konkretnej wersji. Nie zna przyszłej
 * treści ani uzasadnienia. Exact assessment wymaga już kompletnego polecenia.
 */
public record OperationAvailability(long version, Map<WorkflowCommand.Operation, CommandAssessment> operations) {

    public OperationAvailability {
        if (version < 1)
            throw new IllegalArgumentException("Niepoprawna wersja");
        operations = Map.copyOf(operations);
        if (operations.size() != WorkflowCommand.Operation.values().length)
            throw new IllegalArgumentException("Niepełny raport operacji");
        for (var operation : WorkflowCommand.Operation.values()) if (!operations.containsKey(operation))
            throw new IllegalArgumentException("Brak operacji");
    }
}
