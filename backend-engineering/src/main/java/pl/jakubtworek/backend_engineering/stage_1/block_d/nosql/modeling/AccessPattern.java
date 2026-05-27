package pl.jakubtworek.backend_engineering.stage_1.block_d.nosql.modeling;

import java.util.List;

/**
 * Opis pojedynczego access patternu.
 *
 * W NoSQL model danych powinien wynikać z tego,
 * jak aplikacja czyta i zapisuje dane.
 */
public record AccessPattern(
        String name,
        OperationType operationType,
        String accessKey,
        List<String> filters,
        List<String> sortBy,
        ConsistencyRequirement consistencyRequirement
) {

    public boolean requiresStrongConsistency() {
        return consistencyRequirement == ConsistencyRequirement.STRONG;
    }

    public enum OperationType {
        READ,
        WRITE,
        UPDATE,
        DELETE
    }

    public enum ConsistencyRequirement {
        STRONG,
        EVENTUAL
    }
}
