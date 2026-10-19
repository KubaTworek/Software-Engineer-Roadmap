package pl.jakubtworek.backend_engineering.stage_1.block_d.nosql.consistency;

import java.util.Objects;

/** Snapshot wartości używany przez conditional write / compare-and-set. */
public record VersionedValue<T>(T value, long version) {

    public VersionedValue {
        value = Objects.requireNonNull(value, "value must not be null");
        if (version < 0) {
            throw new IllegalArgumentException("version must not be negative");
        }
    }
}
