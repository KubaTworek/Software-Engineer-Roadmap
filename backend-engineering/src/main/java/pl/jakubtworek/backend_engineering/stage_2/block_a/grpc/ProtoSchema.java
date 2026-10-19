package pl.jakubtworek.backend_engineering.stage_2.block_a.grpc;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class ProtoSchema {

    private final Map<Integer, ProtoField> fieldsByNumber;
    private final Set<Integer> reservedNumbers;

    public ProtoSchema(List<ProtoField> fields, Set<Integer> reservedNumbers) {
        this.reservedNumbers = Set.copyOf(reservedNumbers);
        this.fieldsByNumber = new HashMap<>();
        for (ProtoField field : fields) {
            if (this.reservedNumbers.contains(field.number())) {
                throw new IllegalArgumentException("active field uses reserved number " + field.number());
            }
            if (fieldsByNumber.put(field.number(), field) != null) {
                throw new IllegalArgumentException("duplicate field number " + field.number());
            }
        }
    }

    Map<Integer, ProtoField> fieldsByNumber() {
        return Map.copyOf(fieldsByNumber);
    }

    Set<Integer> reservedNumbers() {
        return reservedNumbers;
    }
}
