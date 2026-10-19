package pl.jakubtworek.backend_engineering.stage_2.block_a.grpc;

import java.util.ArrayList;
import java.util.List;

/** Conservative wire-contract review for the field-number rules most often violated. */
public final class ProtoCompatibilityChecker {

    public List<String> safeEvolutionViolations(ProtoSchema previous, ProtoSchema candidate) {
        List<String> violations = new ArrayList<>();
        previous.fieldsByNumber().forEach((number, oldField) -> {
            ProtoField newField = candidate.fieldsByNumber().get(number);
            if (newField == null) {
                if (!candidate.reservedNumbers().contains(number)) {
                    violations.add("removed field number " + number + " must be reserved");
                }
                return;
            }
            if (oldField.type() != newField.type()) {
                violations.add("field number " + number + " changed wire type");
            }
            if (!oldField.name().equals(newField.name())) {
                violations.add("field number " + number + " was reused by " + newField.name());
            }
        });
        return List.copyOf(violations);
    }
}
