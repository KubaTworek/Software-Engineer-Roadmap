package pl.jakubtworek.backend_engineering.stage_1.block_d.nosql.modeling;

import java.util.List;
import java.util.Objects;

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

    public AccessPattern {
        name = requireNonBlank(name, "name");
        operationType = Objects.requireNonNull(operationType, "operationType must not be null");
        accessKey = requireNonBlank(accessKey, "accessKey");
        filters = List.copyOf(Objects.requireNonNull(filters, "filters must not be null"));
        sortBy = List.copyOf(Objects.requireNonNull(sortBy, "sortBy must not be null"));
        consistencyRequirement = Objects.requireNonNull(
                consistencyRequirement,
                "consistencyRequirement must not be null"
        );
    }

    public boolean requiresStrongConsistency() {
        return consistencyRequirement == ConsistencyRequirement.STRONG;
    }

    private static String requireNonBlank(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value;
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
