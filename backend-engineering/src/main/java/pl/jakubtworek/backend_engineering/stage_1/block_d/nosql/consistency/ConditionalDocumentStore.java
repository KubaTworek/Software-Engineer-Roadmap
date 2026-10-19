package pl.jakubtworek.backend_engineering.stage_1.block_d.nosql.consistency;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Minimalny in-memory odpowiednik conditional write znanego m.in. z DynamoDB
 * i dokumentów wersjonowanych. Atomowość zapewnia tu monitor JVM; w prawdziwym
 * systemie warunek musi zostać wykonany po stronie magazynu danych.
 */
public final class ConditionalDocumentStore<T> {

    private final Map<String, VersionedValue<T>> documents = new HashMap<>();

    public synchronized VersionedValue<T> create(String id, T value) {
        requireId(id);
        Objects.requireNonNull(value, "value must not be null");
        if (documents.containsKey(id)) {
            throw new IllegalStateException("document already exists: " + id);
        }
        VersionedValue<T> created = new VersionedValue<>(value, 1);
        documents.put(id, created);
        return created;
    }

    public synchronized Optional<VersionedValue<T>> find(String id) {
        requireId(id);
        return Optional.ofNullable(documents.get(id));
    }

    public synchronized ConditionalWriteResult<T> replaceIfVersion(
            String id,
            long expectedVersion,
            T newValue
    ) {
        requireId(id);
        Objects.requireNonNull(newValue, "newValue must not be null");
        VersionedValue<T> current = documents.get(id);
        if (current == null) {
            return new ConditionalWriteResult<>(false, Optional.empty());
        }
        if (current.version() != expectedVersion) {
            return new ConditionalWriteResult<>(false, Optional.of(current));
        }
        VersionedValue<T> updated = new VersionedValue<>(newValue, Math.addExact(expectedVersion, 1));
        documents.put(id, updated);
        return new ConditionalWriteResult<>(true, Optional.of(updated));
    }

    private static void requireId(String id) {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("id must not be blank");
        }
    }

    public record ConditionalWriteResult<T>(boolean applied, Optional<VersionedValue<T>> current) {
        public ConditionalWriteResult {
            current = Objects.requireNonNull(current, "current must not be null");
        }
    }
}
